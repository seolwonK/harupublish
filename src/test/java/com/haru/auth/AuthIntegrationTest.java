package com.haru.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void signupLoginMeRefreshLogoutFlow() throws Exception {
        AuthTokens signupTokens = signup("learner@example.com", "password123!", "Learner");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + signupTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("learner@example.com"))
                .andExpect(jsonPath("$.data.activeRole").value("STUDENT"))
                .andExpect(jsonPath("$.data.roles", containsInAnyOrder("STUDENT")));

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "password": "password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.lastLoginAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AuthTokens loginTokens = tokens(loginResponse);

        Integer lastLoginCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND last_login_at IS NOT NULL",
                Integer.class,
                "learner@example.com"
        );
        org.assertj.core.api.Assertions.assertThat(lastLoginCount).isEqualTo(1);

        String refreshResponse = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(loginTokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").value(not(loginTokens.refreshToken())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        AuthTokens rotatedTokens = tokens(refreshResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(loginTokens.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + rotatedTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(rotatedTokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(rotatedTokens.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void duplicateSignupIsRejected() throws Exception {
        signup("duplicate@example.com", "password123!", "Learner");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "duplicate@example.com",
                                  "password": "password123!",
                                  "name": "Learner",
                                  "timeZone": "Asia/Seoul"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void activeRoleMustBeAssignedToUser() throws Exception {
        AuthTokens tokens = signup("role@example.com", "password123!", "Learner");

        mockMvc.perform(patch("/api/users/me/active-role")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "activeRole": "TUTOR"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROLE_NOT_ASSIGNED"));
    }

    @Test
    void inactiveAccountCannotLogin() throws Exception {
        signup("suspended@example.com", "password123!", "Suspended");
        jdbcTemplate.update("UPDATE users SET account_status = 'SUSPENDED' WHERE email = ?", "suspended@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "suspended@example.com",
                                  "password": "password123!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    void invalidPasswordCannotLogin() throws Exception {
        signup("wrong-password@example.com", "password123!", "Learner");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "wrong-password@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void adminIdCanLoginWithoutEmailFormat() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO users (
                    email,
                    password_hash,
                    name,
                    mobile_number,
                    time_zone,
                    active_role,
                    account_status,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, NULL, ?, 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, "admin-test", passwordEncoder.encode("admin1234"), "Admin", "Asia/Seoul");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role) SELECT id, 'STUDENT' FROM users WHERE email = ?", "admin-test");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role) SELECT id, 'ADMIN' FROM users WHERE email = ?", "admin-test");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin-test",
                                  "password": "admin1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("admin-test"))
                .andExpect(jsonPath("$.data.user.activeRole").value("ADMIN"))
                .andExpect(jsonPath("$.data.user.roles", containsInAnyOrder("STUDENT", "ADMIN")));
    }

    private AuthTokens signup(String email, String password, String name) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "name": "%s",
                                  "timeZone": "Asia/Seoul"
                                }
                                """.formatted(email, password, name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.activeRole").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.roles", containsInAnyOrder("STUDENT")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return tokens(response);
    }

    private AuthTokens tokens(String response) throws Exception {
        JsonNode data = objectMapper.readTree(response).get("data");
        return new AuthTokens(data.get("accessToken").asText(), data.get("refreshToken").asText());
    }

    private record AuthTokens(String accessToken, String refreshToken) {
    }
}
