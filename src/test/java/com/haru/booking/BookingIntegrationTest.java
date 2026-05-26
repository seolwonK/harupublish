package com.haru.booking;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @DynamicPropertySource
    static void jitsiProperties(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKey = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + privateKey + "\n-----END PRIVATE KEY-----";

        registry.add("haru.jitsi.jaas.enabled", () -> "true");
        registry.add("haru.jitsi.jaas.app-id", () -> "test-app");
        registry.add("haru.jitsi.jaas.key-id", () -> "test-key");
        registry.add("haru.jitsi.jaas.private-key-pem", () -> pem);
        registry.add("haru.jitsi.jaas.domain", () -> "8x8.vc");
    }

    @Test
    void studentCanBookApprovedTutorScheduleAndTutorCanReadIt() throws Exception {
        String tutorToken = signupAndGetAccessToken("booking-tutor@example.com");
        long tutorProfileId = createApprovedTutorWithSchedule(tutorToken, "2031-05-20T01:00:00Z");
        long scheduleSlotId = firstScheduleSlotId(tutorToken, "2031-05-20T00:00:00Z", "2031-05-20T03:00:00Z");
        String studentToken = signupAndGetAccessToken("booking-student@example.com");
        createCheckout(studentToken, tutorProfileId);

        String bookingResponse = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorProfileId": %d,
                                  "scheduleSlotId": %d,
                                  "lessonDurationMinutes": 25
                                }
                                """.formatted(tutorProfileId, scheduleSlotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorProfileId").value(tutorProfileId))
                .andExpect(jsonPath("$.data.tutorDisplayName").value("Booking Korean Expert"))
                .andExpect(jsonPath("$.data.tutorShortIntroduction").value("Practical Korean lessons for work and travel."))
                .andExpect(jsonPath("$.data.scheduleSlotId").value(scheduleSlotId))
                .andExpect(jsonPath("$.data.lessonDurationMinutes").value(25))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long bookingId = objectMapper.readTree(bookingResponse).get("data").get("id").asLong();

        mockMvc.perform(get("/api/bookings/me")
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookings.length()").value(1))
                .andExpect(jsonPath("$.data.bookings[0].id").value(bookingId));

        mockMvc.perform(get("/api/tutors/%d/schedule".formatted(tutorProfileId))
                        .param("from", "2031-05-20T00:00:00Z")
                        .param("to", "2031-05-20T03:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots[0].id").value(scheduleSlotId))
                .andExpect(jsonPath("$.data.slots[0].booked").value(true));

        mockMvc.perform(patch("/api/users/me/active-role")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeRole\":\"TUTOR\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/me")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookings.length()").value(0));

        mockMvc.perform(get("/api/bookings/me")
                        .param("participant", "tutor")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookings.length()").value(1))
                .andExpect(jsonPath("$.data.bookings[0].id").value(bookingId))
                .andExpect(jsonPath("$.data.bookings[0].studentName").value("Test User"));

        mockMvc.perform(get("/api/bookings/%d/join".formatted(bookingId))
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joinAvailable").value(false))
                .andExpect(jsonPath("$.data.joinUrl").doesNotExist())
                .andExpect(jsonPath("$.data.jwt").doesNotExist());
    }

    @Test
    void bookingValidationAndAccessScenarios() throws Exception {
        String tutorToken = signupAndGetAccessToken("booking-validation-tutor@example.com");
        long tutorProfileId = switchToTutorAndGetProfileId(tutorToken);
        saveCompleteTutorProfile(tutorToken);
        saveSchedule(tutorToken, "2031-05-21T01:00:00Z");
        long scheduleSlotId = firstScheduleSlotId(tutorToken, "2031-05-21T00:00:00Z", "2031-05-21T03:00:00Z");
        String studentToken = signupAndGetAccessToken("booking-validation-student@example.com");

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(tutorProfileId, scheduleSlotId, 25)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk());
        approveTutorProfile(tutorProfileId);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(tutorProfileId, scheduleSlotId, 25)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        createCheckout(studentToken, tutorProfileId);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(tutorProfileId, scheduleSlotId, 50)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        String bookingResponse = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(tutorProfileId, scheduleSlotId, 25)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long bookingId = objectMapper.readTree(bookingResponse).get("data").get("id").asLong();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(tutorProfileId, scheduleSlotId, 25)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        String otherUserToken = signupAndGetAccessToken("booking-other@example.com");
        mockMvc.perform(get("/api/bookings/%d".formatted(bookingId))
                        .header("Authorization", auth(otherUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelAndJoinPoliciesUseLessonStartTime() throws Exception {
        String tutorToken = signupAndGetAccessToken("booking-policy-tutor@example.com");
        long tutorProfileId = createApprovedTutorWithSchedule(tutorToken, "2031-05-22T01:00:00Z");
        long futureSlotId = firstScheduleSlotId(tutorToken, "2031-05-22T00:00:00Z", "2031-05-22T03:00:00Z");
        String studentToken = signupAndGetAccessToken("booking-policy-student@example.com");
        createCheckout(studentToken, tutorProfileId);

        long futureBookingId = createBooking(studentToken, tutorProfileId, futureSlotId);
        mockMvc.perform(patch("/api/bookings/%d/cancel".formatted(futureBookingId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Schedule changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Schedule changed"));

        String nearTutorToken = signupAndGetAccessToken("booking-policy-near-tutor@example.com");
        long nearTutorProfileId = createApprovedTutorWithSchedule(nearTutorToken, "2026-05-18T00:00:00Z");
        long pastSlotId = firstScheduleSlotId(nearTutorToken, "2026-05-18T00:00:00Z", "2026-05-18T01:00:00Z");
        createCheckout(studentToken, nearTutorProfileId);
        long pastBookingId = createBooking(studentToken, nearTutorProfileId, pastSlotId);

        mockMvc.perform(patch("/api/bookings/%d/cancel".formatted(pastBookingId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too late\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/bookings/%d".formatted(pastBookingId))
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.joinAvailable").value(false));

        mockMvc.perform(get("/api/bookings/%d/join".formatted(pastBookingId))
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joinAvailable").value(false))
                .andExpect(jsonPath("$.data.joinUrl").doesNotExist())
                .andExpect(jsonPath("$.data.jwt").doesNotExist());
    }

    private long createApprovedTutorWithSchedule(String tutorToken, String startAt) throws Exception {
        long tutorProfileId = switchToTutorAndGetProfileId(tutorToken);
        saveCompleteTutorProfile(tutorToken);
        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk());
        approveTutorProfile(tutorProfileId);
        saveSchedule(tutorToken, startAt);
        return tutorProfileId;
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

    private void saveCompleteTutorProfile(String accessToken) throws Exception {
        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Booking Korean Expert",
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

    private void approveTutorProfile(long tutorProfileId) throws Exception {
        String adminAccessToken = createAdminAndLogin("admin-booking-%d@example.com".formatted(tutorProfileId));
        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminAccessToken)))
                .andExpect(status().isOk());
    }

    private void saveSchedule(String tutorToken, String startAt) throws Exception {
        mockMvc.perform(put("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    { "startAt": "%s" }
                                  ]
                                }
                                """.formatted(startAt)))
                .andExpect(status().isOk());
    }

    private long firstScheduleSlotId(String tutorToken, String from, String to) throws Exception {
        String response = mockMvc.perform(get("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorToken))
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data").get("slots").get(0).get("id").asLong();
    }

    private long createBooking(String studentToken, long tutorProfileId, long scheduleSlotId) throws Exception {
        String response = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(tutorProfileId, scheduleSlotId, 25)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

                private long createCheckout(String studentToken, long tutorProfileId) throws Exception {
                                String response = mockMvc.perform(post("/api/payments/checkout")
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
                                                                .andExpect(status().isOk())
                                                                .andReturn()
                                                                .getResponse()
                                                                .getContentAsString();
                                return objectMapper.readTree(response).get("data").get("id").asLong();
                }

    private String bookingJson(long tutorProfileId, long scheduleSlotId, int durationMinutes) {
        return """
                {
                  "tutorProfileId": %d,
                  "scheduleSlotId": %d,
                  "lessonDurationMinutes": %d
                }
                """.formatted(tutorProfileId, scheduleSlotId, durationMinutes);
    }

    private String createAdminAndLogin(String email) throws Exception {
        signupAndGetAccessToken(email);
        UserAccount admin = userAccountRepository.findByEmail(email).orElseThrow();
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
