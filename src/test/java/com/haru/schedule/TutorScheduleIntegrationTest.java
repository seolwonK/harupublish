package com.haru.schedule;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TutorScheduleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Test
    void tutorCanReplaceAndReadScheduleSlots() throws Exception {
        String tutorAccessToken = signupAndGetAccessToken("schedule-tutor@example.com");
        long tutorProfileId = switchToTutorAndGetProfileId(tutorAccessToken);

        mockMvc.perform(put("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    { "startAt": "2026-05-20T01:30:00Z" },
                                    { "startAt": "2026-05-20T01:00:00Z" },
                                    { "startAt": "2026-05-20T01:00:00Z" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots.length()").value(2))
                .andExpect(jsonPath("$.data.slots[0].startAt").value("2026-05-20T01:00:00Z"))
                .andExpect(jsonPath("$.data.slots[0].endAt").value("2026-05-20T01:30:00Z"))
                .andExpect(jsonPath("$.data.slots[1].startAt").value("2026-05-20T01:30:00Z"))
                .andExpect(jsonPath("$.data.slots[1].endAt").value("2026-05-20T02:00:00Z"));

        mockMvc.perform(get("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorAccessToken))
                        .param("from", "2026-05-20T00:00:00Z")
                        .param("to", "2026-05-20T02:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots.length()").value(2));

        mockMvc.perform(get("/api/tutors/%d/schedule".formatted(tutorProfileId))
                        .param("from", "2026-05-20T00:00:00Z")
                        .param("to", "2026-05-20T02:00:00Z"))
                .andExpect(status().isNotFound());

        approveTutorProfile(tutorAccessToken, tutorProfileId);

        mockMvc.perform(get("/api/tutors/%d/schedule".formatted(tutorProfileId))
                        .param("from", "2026-05-20T00:00:00Z")
                        .param("to", "2026-05-20T02:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots.length()").value(2))
                .andExpect(jsonPath("$.data.slots[0].startAt").value("2026-05-20T01:00:00Z"));
    }

    @Test
    void scheduleValidationScenarios() throws Exception {
        String tutorAccessToken = signupAndGetAccessToken("schedule-validation@example.com");
        switchToTutorAndGetProfileId(tutorAccessToken);

        mockMvc.perform(put("/api/tutors/me/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slots\":[]}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/tutors/me/schedule")
                        .param("from", "2026-05-20T00:00:00Z")
                        .param("to", "2026-05-20T02:00:00Z"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    { "startAt": "2026-05-20T01:15:00Z" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorAccessToken))
                        .param("from", "2026-05-20T02:00:00Z")
                        .param("to", "2026-05-20T02:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void replacingSchedulePreservesReservedSlots() throws Exception {
        String tutorAccessToken = signupAndGetAccessToken("schedule-booked-tutor@example.com");
        long tutorProfileId = switchToTutorAndGetProfileId(tutorAccessToken);
        approveTutorProfile(tutorAccessToken, tutorProfileId);

        saveSchedule(tutorAccessToken, "2031-05-20T01:00:00Z", "2031-05-20T01:30:00Z");
        long bookedSlotId = firstScheduleSlotId(tutorAccessToken, "2031-05-20T00:00:00Z", "2031-05-20T03:00:00Z");

        String studentToken = signupAndGetAccessToken("schedule-booked-student@example.com");
        createPaidCheckout(studentToken, tutorProfileId);
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorProfileId": %d,
                                  "scheduleSlotId": %d,
                                  "lessonDurationMinutes": 25
                                }
                                """.formatted(tutorProfileId, bookedSlotId)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    { "startAt": "2031-05-20T02:00:00Z" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots.length()").value(2))
                .andExpect(jsonPath("$.data.slots[0].id").value(bookedSlotId))
                .andExpect(jsonPath("$.data.slots[0].booked").value(true))
                .andExpect(jsonPath("$.data.slots[1].startAt").value("2031-05-20T02:00:00Z"));
    }

    private long switchToTutorAndGetProfileId(String accessToken) throws Exception {
        String response = mockMvc.perform(post("/api/tutors/me/switch")
                        .header("Authorization", auth(accessToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    private void approveTutorProfile(String tutorAccessToken, long tutorProfileId) throws Exception {
        saveCompleteTutorProfile(tutorAccessToken);
        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorAccessToken)))
                .andExpect(status().isOk());

        String adminAccessToken = createAdminAndLogin("admin-schedule-flow-%d@example.com".formatted(tutorProfileId));
        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminAccessToken)))
                .andExpect(status().isOk());
    }

    private void saveCompleteTutorProfile(String accessToken) throws Exception {
        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Schedule Korean Expert",
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
                .andExpect(status().isOk());
    }

    private void saveSchedule(String tutorAccessToken, String... startTimes) throws Exception {
        String slotsJson = java.util.Arrays.stream(startTimes)
                .map(startAt -> "{ \"startAt\": \"%s\" }".formatted(startAt))
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(put("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slots\":[%s]}".formatted(slotsJson)))
                .andExpect(status().isOk());
    }

    private long firstScheduleSlotId(String tutorAccessToken, String from, String to) throws Exception {
        String response = mockMvc.perform(get("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorAccessToken))
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data").get("slots").get(0).get("id").asLong();
    }

    private void createPaidCheckout(String studentToken, long tutorProfileId) throws Exception {
        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorProfileId": %d,
                                  "lessonDurationMinutes": 25,
                                  "lessonPackCount": 1,
                                  "paymentMethod": "LEMON_SQUEEZY"
                                }
                                """.formatted(tutorProfileId)))
                .andExpect(status().isOk());
    }

    private String createAdminAndLogin(String email) throws Exception {
        signupAndGetAccessToken(email);
        UserAccount admin = userAccountRepository.findByEmail(email)
                .orElseThrow();
        admin.addRole(Role.ADMIN);
        admin.changeActiveRole(Role.ADMIN);
        userAccountRepository.save(admin);
        return loginAndGetAccessToken(email);
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

    private String auth(String accessToken) {
        return "Bearer " + accessToken;
    }
}
