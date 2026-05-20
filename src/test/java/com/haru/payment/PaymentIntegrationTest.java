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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    void studentCanCreateCheckoutAndReadOwnPayments() throws Exception {
        String tutorToken = signupAndGetAccessToken("payment-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken);
        String studentToken = signupAndGetAccessToken("payment-student@example.com");

        String response = mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 1, "CARD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentUserId").exists())
                .andExpect(jsonPath("$.data.tutorProfileId").value(tutorProfileId))
                .andExpect(jsonPath("$.data.lessonDurationMinutes").value(25))
                .andExpect(jsonPath("$.data.lessonPackCount").value(1))
                .andExpect(jsonPath("$.data.unitAmount").value(100.00))
                .andExpect(jsonPath("$.data.subtotalAmount").value(100.00))
                .andExpect(jsonPath("$.data.discountAmount").value(0.00))
                .andExpect(jsonPath("$.data.studentFeeAmount").value(5.00))
                .andExpect(jsonPath("$.data.totalAmount").value(105.00))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long paymentId = objectMapper.readTree(response).get("data").get("id").asLong();

        mockMvc.perform(get("/api/payments/me")
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payments.length()").value(1))
                .andExpect(jsonPath("$.data.payments[0].id").value(paymentId));

        mockMvc.perform(get("/api/payments/%d".formatted(paymentId))
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paymentId));
    }

    @Test
    void lessonPackDiscountsAndFiftyMinutePriceAreCalculated() throws Exception {
        String tutorToken = signupAndGetAccessToken("payment-calculation-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken);
        String studentToken = signupAndGetAccessToken("payment-calculation-student@example.com");

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 5, "PAYPAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotalAmount").value(500.00))
                .andExpect(jsonPath("$.data.discountAmount").value(25.00))
                .andExpect(jsonPath("$.data.studentFeeAmount").value(23.75))
                .andExpect(jsonPath("$.data.totalAmount").value(498.75));

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 10, "SIMPLE_PAY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotalAmount").value(1000.00))
                .andExpect(jsonPath("$.data.discountAmount").value(100.00))
                .andExpect(jsonPath("$.data.studentFeeAmount").value(45.00))
                .andExpect(jsonPath("$.data.totalAmount").value(945.00));

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 50, 1, "CARD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitAmount").value(180.00))
                .andExpect(jsonPath("$.data.totalAmount").value(189.00));
    }

    @Test
    void checkoutValidationAndAccessScenarios() throws Exception {
        String tutorToken = signupAndGetAccessToken("payment-validation-tutor@example.com");
        long tutorProfileId = switchToTutorAndGetProfileId(tutorToken);
        saveCompleteTutorProfile(tutorToken);
        String studentToken = signupAndGetAccessToken("payment-validation-student@example.com");

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 1, "CARD")))
                .andExpect(status().isNotFound());

        submitAndApproveTutorProfile(tutorToken, tutorProfileId);

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 30, 1, "CARD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 3, "CARD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 1, "BANK")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 1, "CARD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        String paymentResponse = mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 1, "CARD")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long paymentId = objectMapper.readTree(paymentResponse).get("data").get("id").asLong();
        String otherUserToken = signupAndGetAccessToken("payment-other@example.com");

        mockMvc.perform(get("/api/payments/%d".formatted(paymentId))
                        .header("Authorization", auth(otherUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refundRequestRequiresPaidPayment() throws Exception {
        String tutorToken = signupAndGetAccessToken("payment-refund-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken);
        String studentToken = signupAndGetAccessToken("payment-refund-student@example.com");

        long pendingPaymentId = createCheckout(studentToken, tutorProfileId);
        mockMvc.perform(post("/api/payments/%d/refund-request".formatted(pendingPaymentId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        long paidPaymentId = createCheckout(studentToken, tutorProfileId);
        Payment payment = paymentRepository.findById(paidPaymentId).orElseThrow();
        payment.markPaid();
        paymentRepository.save(payment);

        mockMvc.perform(post("/api/payments/%d/refund-request".formatted(paidPaymentId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Schedule changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUND_REQUESTED"))
                .andExpect(jsonPath("$.data.refundReason").value("Schedule changed"));
    }

    private long createApprovedTutor(String tutorToken) throws Exception {
        long tutorProfileId = switchToTutorAndGetProfileId(tutorToken);
        saveCompleteTutorProfile(tutorToken);
        submitAndApproveTutorProfile(tutorToken, tutorProfileId);
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
                                  "displayName": "Payment Korean Expert",
                                  "shortIntroduction": "Structured Korean lessons for global learners.",
                                  "aboutMe": "I help students build Korean confidence.",
                                  "whatIOffer": "Conversation, reading, and workplace Korean.",
                                  "category": "KOREAN",
                                  "profileImageUrl": "https://example.com/profile.jpg",
                                  "introVideoUrl": "https://youtu.be/haru-payment",
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

    private void submitAndApproveTutorProfile(String tutorToken, long tutorProfileId) throws Exception {
        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk());
        String adminAccessToken = createAdminAndLogin("admin-payment-%d@example.com".formatted(tutorProfileId));
        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminAccessToken)))
                .andExpect(status().isOk());
    }

    private long createCheckout(String studentToken, long tutorProfileId) throws Exception {
        String response = mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 1, "CARD")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    private String checkoutJson(long tutorProfileId, int lessonDurationMinutes, int lessonPackCount, String paymentMethod) {
        return """
                {
                  "tutorProfileId": %d,
                  "lessonDurationMinutes": %d,
                  "lessonPackCount": %d,
                  "paymentMethod": "%s"
                }
                """.formatted(tutorProfileId, lessonDurationMinutes, lessonPackCount, paymentMethod);
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
