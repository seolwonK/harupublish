package com.haru;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiScenarioIntegrationTest {

    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Test
    void authAndUserApiScenarios() throws Exception {
        String email = uniqueEmail("scenario-user");

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/tutors"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short",
                                  "name": "",
                                  "timeZone": "Asia/Seoul"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        AuthTokens signupTokens = signup(email);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", auth(signupTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles", containsInAnyOrder("STUDENT")))
                .andExpect(jsonPath("$.data.activeRole").value("STUDENT"))
                .andExpect(jsonPath("$.data.tutorProfileStatus").doesNotExist());

        mockMvc.perform(patch("/api/users/me/active-role")
                        .header("Authorization", auth(signupTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "activeRole": "TUTOR"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROLE_NOT_ASSIGNED"));

        mockMvc.perform(patch("/api/users/me/active-role")
                        .header("Authorization", auth(signupTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "activeRole": "NOT_A_ROLE"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", auth(signupTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Scenario User",
                                  "mobileNumber": "+82 10-1234-5678",
                                  "timeZone": "Not/AZone"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", auth(signupTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Scenario User",
                                  "mobileNumber": "+82 10-1234-5678",
                                  "timeZone": "America/New_York"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Scenario User"))
                .andExpect(jsonPath("$.data.timeZone").value("America/New_York"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "wrong-password"
                                }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        AuthTokens loginTokens = login(email);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "not-a-refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

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
                        .header("Authorization", auth(rotatedTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", auth(rotatedTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(rotatedTokens.refreshToken())))
                .andExpect(status().isOk());

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
    void tutorAndAdminApiScenarios() throws Exception {
        String tutorEmail = uniqueEmail("scenario-tutor");
        AuthTokens tutorTokens = signup(tutorEmail);

        mockMvc.perform(get("/api/tutors/me/profile")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(tutorTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        String switchResponse = mockMvc.perform(post("/api/tutors/me/switch")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long tutorProfileId = objectMapper.readTree(switchResponse).get("data").get("id").asLong();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", containsInAnyOrder("STUDENT", "TUTOR")))
                .andExpect(jsonPath("$.data.activeRole").value("TUTOR"))
                .andExpect(jsonPath("$.data.tutorProfileStatus").value("DRAFT"));

        mockMvc.perform(patch("/api/users/me/active-role")
                        .header("Authorization", auth(tutorTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "activeRole": "STUDENT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeRole").value("STUDENT"))
                .andExpect(jsonPath("$.data.tutorProfileStatus").value("DRAFT"));

        mockMvc.perform(patch("/api/users/me/active-role")
                        .header("Authorization", auth(tutorTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "activeRole": "TUTOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeRole").value("TUTOR"));

        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        AuthTokens adminTokens = createAdminAndLogin(uniqueEmail("scenario-admin"));

        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminTokens.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        saveCompleteTutorProfile(tutorTokens.accessToken(), "Scenario Korean Expert", 25, 45);

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        assertThat(expertsContains(tutorProfileId)).isFalse();

        mockMvc.perform(get("/api/tutors/%d".formatted(tutorProfileId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mockMvc.perform(patch("/api/admin/tutors/%d/reject".formatted(tutorProfileId))
                        .header("Authorization", auth(adminTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorProfileStatus").value("REJECTED"));

        saveCompleteTutorProfile(tutorTokens.accessToken(), "Scenario Korean Expert", 25, 45);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorProfileStatus").value("DRAFT"));

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(expertsContains(tutorProfileId)).isTrue();

        mockMvc.perform(get("/api/tutors/%d".formatted(tutorProfileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tutorProfileId))
                .andExpect(jsonPath("$.data.category").value("KOREAN"))
                .andExpect(jsonPath("$.data.availableLanguages[0]").value("Korean"))
                .andExpect(jsonPath("$.data.lessonPrice25Amount").value(25.00))
                .andExpect(jsonPath("$.data.lessonPrice50Amount").value(45.00));

        saveCompleteTutorProfile(tutorTokens.accessToken(), "Edited Korean Expert", 30000, 55000);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorProfileStatus").value("DRAFT"));
        assertThat(expertsContains(tutorProfileId)).isFalse();

        mockMvc.perform(get("/api/tutors/%d".formatted(tutorProfileId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/tutors/%d/reject".formatted(tutorProfileId))
                        .header("Authorization", auth(adminTokens.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(expertsContains(tutorProfileId)).isTrue();

        mockMvc.perform(patch("/api/admin/tutors/999999/approve")
                        .header("Authorization", auth(adminTokens.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void structuredTutorProfileValidationScenarios() throws Exception {
        AuthTokens tutorTokens = signup(uniqueEmail("structured-tutor"));

        mockMvc.perform(post("/api/tutors/me/switch")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(tutorTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeTutorProfileJson("https://example.com/intro.mp4", 25, 45, "[\"Korean\", \"English\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.introVideoUrl").value("https://example.com/intro.mp4"));

        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(tutorTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeTutorProfileJson("https://youtu.be/haru-intro", 25, 45, "[\"Korean\", \"English\"]").replace("\"category\": \"KOREAN\"", "\"category\": \"INVALID\"")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(tutorTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeTutorProfileJson("https://youtu.be/haru-intro", 0, 45, "[\"Korean\", \"English\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(tutorTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeTutorProfileJson("https://youtu.be/haru-intro", 25, 45, "[]")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorTokens.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    private AuthTokens signup(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.activeRole").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.roles", containsInAnyOrder("STUDENT")))
                .andExpect(jsonPath("$.data.user.tutorProfileStatus").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return tokens(response);
    }

    private AuthTokens login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.lastLoginAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return tokens(response);
    }

    private AuthTokens createAdminAndLogin(String email) throws Exception {
        signup(email);
        UserAccount admin = userAccountRepository.findByEmail(email).orElseThrow();
        admin.addRole(Role.ADMIN);
        admin.changeActiveRole(Role.ADMIN);
        userAccountRepository.save(admin);
        return login(email);
    }

    private void saveCompleteTutorProfile(String accessToken, String displayName, int lessonPrice25Amount, int lessonPrice50Amount) throws Exception {
        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeTutorProfileJson("https://youtu.be/haru-intro", lessonPrice25Amount, lessonPrice50Amount, "[\"Korean\", \"English\"]")
                                .replace("Scenario Korean Expert", displayName)))
                .andExpect(status().isOk());
    }

    private String completeTutorProfileJson(String introVideoUrl, int lessonPrice25Amount, int lessonPrice50Amount, String availableLanguagesJson) {
        return """
                {
                  "displayName": "Scenario Korean Expert",
                  "shortIntroduction": "Practical Korean lessons for work and travel.",
                  "aboutMe": "I help learners speak Korean with confidence.",
                  "whatIOffer": "Conversation, pronunciation, and interview preparation.",
                  "category": "KOREAN",
                  "profileImageUrl": "https://example.com/profile.jpg",
                  "introVideoUrl": "%s",
                  "thumbnailUrl": "https://example.com/thumb.jpg",
                  "availableLanguages": %s,
                  "lessonPrice25Amount": %d,
                  "lessonPrice50Amount": %d,
                  "availableTimeNote": "Weekday evenings KST",
                  "paymentMethod": "BANK_TRANSFER"
                }
                """.formatted(introVideoUrl, availableLanguagesJson, lessonPrice25Amount, lessonPrice50Amount);
    }

    private boolean expertsContains(long tutorProfileId) throws Exception {
        String response = mockMvc.perform(get("/api/tutors"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (JsonNode node : objectMapper.readTree(response).get("data")) {
            if (node.get("tutorProfileId").asLong() == tutorProfileId) {
                return true;
            }
        }
        return false;
    }

    private AuthTokens tokens(String response) throws Exception {
        JsonNode data = objectMapper.readTree(response).get("data");
        return new AuthTokens(data.get("accessToken").asText(), data.get("refreshToken").asText());
    }

    private String signupJson(String email) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "name": "Scenario User",
                  "timeZone": "Asia/Seoul"
                }
                """.formatted(email, PASSWORD);
    }

    private String auth(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private record AuthTokens(String accessToken, String refreshToken) {
    }
}
