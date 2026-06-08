package com.haru.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.payment.domain.Payment;
import com.haru.payment.infra.PaymentRepository;
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

/**
 * Covers the backend halves of #9 (student-facing 25-min price in catalog DTOs) and
 * #11 (admin refund-request queue) including ADMIN protection.
 */
@SpringBootTest(properties = "haru.payments.lemon-squeezy.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRefundRequestsAndStudentPriceTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PaymentRepository paymentRepository;

    // Active platform settings seed has student_fee_rate = 0.1000 (10%).
    // tutor lessonPrice25Amount = 100.00 -> studentPrice25Amount = 110.00.

    @Test
    void catalogExposesStudentPrice25Amount() throws Exception {
        String tutorToken = signupAndGetAccessToken("price-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "price-admin@example.com");

        // Public experts list.
        mockMvc.perform(get("/api/tutors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lessonPrice25Amount").value(100.00))
                .andExpect(jsonPath("$.data[0].studentPrice25Amount").value(110.00));

        // Public expert detail.
        mockMvc.perform(get("/api/tutors/%d".formatted(tutorProfileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonPrice25Amount").value(100.00))
                .andExpect(jsonPath("$.data.studentPrice25Amount").value(110.00));
    }

    @Test
    void refundRequestsListIsAdminProtectedAndReturnsRequestedPayments() throws Exception {
        String tutorToken = signupAndGetAccessToken("refund-list-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "refund-list-admin@example.com");
        String studentToken = signupAndGetAccessToken("refund-list-student@example.com");

        long paidPaymentId = createCheckout(studentToken, tutorProfileId);
        Payment payment = paymentRepository.findById(paidPaymentId).orElseThrow();
        payment.markPaid();
        paymentRepository.save(payment);

        mockMvc.perform(post("/api/payments/%d/refund-request".formatted(paidPaymentId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Schedule changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUND_REQUESTED"));

        // Anonymous = 401, non-admin (student) = 403.
        mockMvc.perform(get("/api/admin/payments/refund-requests"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/payments/refund-requests")
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isForbidden());

        // Admin sees the requested payment with the documented fields.
        String adminToken = createAdminAndLogin("refund-list-admin2@example.com");
        long studentUserId = userAccountRepository.findByEmail("refund-list-student@example.com")
                .orElseThrow().getId();

        mockMvc.perform(get("/api/admin/payments/refund-requests")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(paidPaymentId))
                .andExpect(jsonPath("$.data[0].studentUserId").value(studentUserId))
                .andExpect(jsonPath("$.data[0].tutorProfileId").value(tutorProfileId))
                .andExpect(jsonPath("$.data[0].totalAmount").value(110.00))
                .andExpect(jsonPath("$.data[0].currency").value("USD"))
                .andExpect(jsonPath("$.data[0].refundReason").value("Schedule changed"))
                .andExpect(jsonPath("$.data[0].createdAt").exists());
    }

    private long createApprovedTutor(String tutorToken, String adminEmail) throws Exception {
        long tutorProfileId = switchToTutorAndGetProfileId(tutorToken);
        saveCompleteTutorProfile(tutorToken);
        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk());
        String adminToken = createAdminAndLogin(adminEmail);
        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
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
                                  "displayName": "Student Price Expert",
                                  "shortIntroduction": "Structured Korean lessons for global learners.",
                                  "aboutMe": "I help students build Korean confidence.",
                                  "whatIOffer": "Conversation, reading, and workplace Korean.",
                                  "category": "KOREAN",
                                  "profileImageUrl": "https://example.com/profile.jpg",
                                  "introVideoUrl": "https://youtu.be/haru-price",
                                  "thumbnailUrl": "https://example.com/thumb.jpg",
                                  "availableLanguages": ["Korean", "English"],
                                  "lessonPrice25Amount": 100.00,
                                  "lessonPrice50Amount": 180.00,
                                  "availableTimeNote": "Weekday evenings KST",
                                  "paymentMethod": "PAYPAL"
                                }
                                """))
                .andExpect(status().isOk());
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
