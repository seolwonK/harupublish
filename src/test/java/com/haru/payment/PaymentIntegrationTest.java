package com.haru.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentMethod;
import com.haru.payment.infra.PaymentRepository;
import com.haru.settings.application.PlatformSettingsService;
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

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "haru.payments.lemon-squeezy.enabled=false")
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

        @Autowired
        TutorProfileRepository tutorProfileRepository;

    @Autowired
    PlatformSettingsService platformSettingsService;

    @Test
    void studentCanCreateCheckoutAndReadOwnPayments() throws Exception {
        String tutorToken = signupAndGetAccessToken("payment-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken);
        String studentToken = signupAndGetAccessToken("payment-student@example.com");

        String response = mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 1, "LEMON_SQUEEZY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentUserId").exists())
                .andExpect(jsonPath("$.data.tutorProfileId").value(tutorProfileId))
                .andExpect(jsonPath("$.data.lessonDurationMinutes").value(25))
                .andExpect(jsonPath("$.data.lessonPackCount").value(1))
                .andExpect(jsonPath("$.data.unitAmount").value(100.00))
                .andExpect(jsonPath("$.data.subtotalAmount").value(100.00))
                .andExpect(jsonPath("$.data.discountAmount").value(0.00))
                .andExpect(jsonPath("$.data.studentFeeAmount").value(10.00))
                .andExpect(jsonPath("$.data.totalAmount").value(110.00))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.displayCurrency").value("USD"))
                .andExpect(jsonPath("$.data.appliedStudentFeeRate").value(0.10))
                .andExpect(jsonPath("$.data.paymentMethod").value("LEMON_SQUEEZY"))
                .andExpect(jsonPath("$.data.status").value("PAID"))
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
    void lessonPackDiscountsUseModel1MarkupAndFiftyMinuteIsRejected() throws Exception {
        String tutorToken = signupAndGetAccessToken("payment-calculation-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken);
        String studentToken = signupAndGetAccessToken("payment-calculation-student@example.com");

        // 5-pack @100: subtotal=500, discount=500*0.05=25, discounted=475,
        // studentFee=475*0.10=47.50, total=522.50
        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 5, "LEMON_SQUEEZY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotalAmount").value(500.00))
                .andExpect(jsonPath("$.data.discountAmount").value(25.00))
                .andExpect(jsonPath("$.data.studentFeeAmount").value(47.50))
                .andExpect(jsonPath("$.data.totalAmount").value(522.50));

        // 10-pack @100: subtotal=1000, discount=1000*0.10=100, discounted=900,
        // studentFee=900*0.10=90, total=990
        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 10, "LEMON_SQUEEZY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotalAmount").value(1000.00))
                .andExpect(jsonPath("$.data.discountAmount").value(100.00))
                .andExpect(jsonPath("$.data.studentFeeAmount").value(90.00))
                .andExpect(jsonPath("$.data.totalAmount").value(990.00));

        // Decision A (MVP): 50-minute lessons cannot be purchased yet.
        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 50, 1, "LEMON_SQUEEZY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
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
                        .content(checkoutJson(tutorProfileId, 25, 1, "LEMON_SQUEEZY")))
                .andExpect(status().isNotFound());

        submitAndApproveTutorProfile(tutorToken, tutorProfileId);

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 30, 1, "LEMON_SQUEEZY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 3, "LEMON_SQUEEZY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 1, "CARD")))
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
                        .content(checkoutJson(tutorProfileId, 25, 1, "LEMON_SQUEEZY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        String paymentResponse = mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(tutorProfileId, 25, 1, "LEMON_SQUEEZY")))
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

        long pendingPaymentId = createPendingCheckout("payment-refund-student@example.com", tutorProfileId);
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

    @Test
    void lemonSqueezyWebhookMarksPaymentPaidThroughDocumentedHyphenEndpoint() throws Exception {
        String tutorToken = signupAndGetAccessToken("payment-webhook-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken);
        String studentEmail = "payment-webhook-student@example.com";
        String studentToken = signupAndGetAccessToken(studentEmail);
        long pendingPaymentId = createPendingCheckout(studentEmail, tutorProfileId);

        String payload = """
                {
                  "meta": {
                    "event_name": "order_created",
                    "custom_data": {
                      "payment_id": "%d"
                    }
                  },
                  "data": {
                    "type": "orders",
                    "id": "98765",
                    "attributes": {
                      "identifier": "order-identifier-98765",
                      "status": "paid",
                      "refunded": false
                    }
                  }
                }
                """.formatted(pendingPaymentId);

        mockMvc.perform(post("/api/payments/webhooks/lemon-squeezy")
                        .header("X-Signature", hmac(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/payments/%d".formatted(pendingPaymentId))
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.providerOrderId").value("98765"))
                .andExpect(jsonPath("$.data.providerOrderIdentifier").value("order-identifier-98765"));
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
                        .content(checkoutJson(tutorProfileId, 25, 1, "LEMON_SQUEEZY")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

        private long createPendingCheckout(String studentEmail, long tutorProfileId) {
                UserAccount student = userAccountRepository.findByEmail(studentEmail).orElseThrow();
                TutorProfile tutorProfile = tutorProfileRepository.findById(tutorProfileId).orElseThrow();
                Payment payment = Payment.checkout(
                                student,
                                tutorProfile,
                                25,
                                1,
                                tutorProfile.getLessonPrice25Amount(),
                        PaymentMethod.LEMON_SQUEEZY,
                        platformSettingsService.currentFeePolicy()
                );
                return paymentRepository.save(payment).getId();
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

    private String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }
}
