package com.haru.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.infra.TutorProfileRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TutorIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

        @Autowired
        TutorProfileRepository tutorProfileRepository;

    @Test
    void tutorProfileRequiresApprovalBeforeExpertsExposure() throws Exception {
        String tutorAccessToken = signupAndGetAccessToken("tutor-flow@example.com");

        mockMvc.perform(get("/api/tutors"))
                .andExpect(status().isOk());

        String switchResponse = mockMvc.perform(post("/api/tutors/me/switch")
                        .header("Authorization", "Bearer " + tutorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long tutorProfileId = objectMapper.readTree(switchResponse).get("data").get("id").asLong();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tutorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.activeRole").value("TUTOR"))
                .andExpect(jsonPath("$.data.tutorProfileStatus").value("DRAFT"));

        mockMvc.perform(post("/api/tutors/me/switch")
                        .header("Authorization", "Bearer " + tutorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tutorProfileId));

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", "Bearer " + tutorAccessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", "Bearer " + tutorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Haru Korean Expert",
                                  "shortIntroduction": "Practical Korean lessons for work and travel.",
                                  "aboutMe": "I help learners speak Korean with confidence.",
                                  "whatIOffer": "Conversation, pronunciation, and interview preparation.",
                                  "category": "KOREAN",
                                  "profileImageUrl": "https://example.com/profile.jpg",
                                  "introVideoUrl": "https://youtu.be/haru-intro",
                                  "thumbnailUrl": "https://example.com/thumb.jpg",
                                  "availableLanguages": ["Korean", "English"],
                                                                                                                                        "lessonPrice25Amount": 25.00,
                                                                                                                                        "lessonPrice50Amount": 45.00,
                                  "availableTimeNote": "Weekday evenings KST",
                                  "paymentMethod": "BANK_TRANSFER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", "Bearer " + tutorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tutorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeRole").value("TUTOR"))
                .andExpect(jsonPath("$.data.tutorProfileStatus").value("PENDING"));

        String pendingExpertsResponse = mockMvc.perform(get("/api/tutors"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(containsTutorProfile(pendingExpertsResponse, tutorProfileId)).isFalse();

        mockMvc.perform(get("/api/tutors/%d".formatted(tutorProfileId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", "Bearer " + tutorAccessToken))
                .andExpect(status().isForbidden());

        String adminAccessToken = createAdminAndLogin();
        mockMvc.perform(get("/api/admin/tutors/pending")
                        .header("Authorization", "Bearer " + tutorAccessToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/tutors/pending")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(tutorProfileId))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tutorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorProfileStatus").value("APPROVED"));

        String approvedExpertsResponse = mockMvc.perform(get("/api/tutors"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(containsTutorProfile(approvedExpertsResponse, tutorProfileId)).isTrue();

        mockMvc.perform(get("/api/tutors/%d".formatted(tutorProfileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tutorProfileId))
                .andExpect(jsonPath("$.data.category").value("KOREAN"))
                .andExpect(jsonPath("$.data.availableLanguages[0]").value("Korean"))
                .andExpect(jsonPath("$.data.lessonPrice25Amount").value(25.00))
                .andExpect(jsonPath("$.data.lessonPrice50Amount").value(45.00));

        TutorProfile hiddenProfile = tutorProfileRepository.findById(tutorProfileId)
                .orElseThrow();
        hiddenProfile.hide();
        tutorProfileRepository.save(hiddenProfile);

        String hiddenExpertsResponse = mockMvc.perform(get("/api/tutors"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(containsTutorProfile(hiddenExpertsResponse, tutorProfileId)).isFalse();

        mockMvc.perform(get("/api/tutors/%d".formatted(tutorProfileId)))
                .andExpect(status().isNotFound());
    }

    private boolean containsTutorProfile(String response, long tutorProfileId) throws Exception {
        for (JsonNode node : objectMapper.readTree(response).get("data")) {
            if (node.get("tutorProfileId").asLong() == tutorProfileId) {
                return true;
            }
        }
        return false;
    }

    private String createAdminAndLogin() throws Exception {
        signupAndGetAccessToken("admin-tutor-flow@example.com");
        UserAccount admin = userAccountRepository.findByEmail("admin-tutor-flow@example.com")
                .orElseThrow();
        admin.addRole(Role.ADMIN);
        admin.changeActiveRole(Role.ADMIN);
        userAccountRepository.save(admin);
        return loginAndGetAccessToken("admin-tutor-flow@example.com");
    }

    private String signupAndGetAccessToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123!",
                                  "name": "Test User",
                                  "timeZone": "Asia/Seoul"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(response).get("data");
        return data.get("accessToken").asText();
    }

    private String loginAndGetAccessToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(response).get("data");
        return data.get("accessToken").asText();
    }
}
