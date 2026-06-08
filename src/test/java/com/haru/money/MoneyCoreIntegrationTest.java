package com.haru.money;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.payment.domain.Payment;
import com.haru.payment.infra.PaymentRepository;
import com.haru.settlement.application.SettlementService;
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

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "haru.payments.lemon-squeezy.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MoneyCoreIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    SettlementService settlementService;

    // --- Refund approve -> Haru credit E2E ---------------------------------

    @Test
    void adminRefundApproveIssuesCreditForUnusedLessonsAndBlocksDoubleRefund() throws Exception {
        String tutorToken = signupAndGetAccessToken("mc-refund-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "mc-refund");
        String studentEmail = "mc-refund-student@example.com";
        String studentToken = signupAndGetAccessToken(studentEmail);

        // 5-pack @100 -> discounted = 500 - 25 = 475, per-unit = 95.00. None used.
        long paymentId = createPaidCheckout(studentToken, tutorProfileId, 5);
        String adminToken = createAdminAndLogin("mc-refund-admin@example.com");

        // refund-approve returns the updated PaymentResponse (status REFUNDED).
        mockMvc.perform(post("/api/admin/payments/%d/refund-approve".formatted(paymentId))
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paymentId))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        // Student sees the credit.
        mockMvc.perform(get("/api/credits/me").header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceAmount").value(475.00))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.studentUserId").exists())
                .andExpect(jsonPath("$.data.ledger[0].entryType").value("REFUND_ISSUED"))
                .andExpect(jsonPath("$.data.ledger[0].amount").value(475.00));

        // Second approve is blocked (already refunded) = no double credit.
        mockMvc.perform(post("/api/admin/payments/%d/refund-approve".formatted(paymentId))
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // --- Tutor earnings + withdrawal hold/reject/reverse -------------------

    @Test
    void withdrawalHoldsBalanceAndRejectReversesIt() throws Exception {
        String tutorToken = signupAndGetAccessToken("mc-wd-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "mc-wd");

        // Seed the tutor ledger with a lesson earning (gross 100, 15% fee -> net 85).
        seedTutorEarning(tutorProfileId, new BigDecimal("100.00"));

        mockMvc.perform(get("/api/tutors/me/earnings").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorProfileId").value(tutorProfileId))
                .andExpect(jsonPath("$.data.availableBalanceAmount").value(85.00))
                .andExpect(jsonPath("$.data.totalEarnedAmount").value(85.00));

        // Request a 50 USD withdrawal via PayPal (3% fee). Holds 50 -> balance 35.
        String withdrawalResponse = mockMvc.perform(post("/api/tutors/me/withdrawals")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"PAYPAL\",\"amount\":50.00,\"payoutAccount\":\"tutor@paypal.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.feeRate").value(0.03))
                .andExpect(jsonPath("$.data.feeAmount").value(1.50))
                .andExpect(jsonPath("$.data.netAmount").value(48.50))
                .andExpect(jsonPath("$.data.payoutAccount").value("tutor@paypal.com"))
                .andExpect(jsonPath("$.data.promoFeeWaived").value(false))
                .andReturn().getResponse().getContentAsString();
        long withdrawalId = objectMapper.readTree(withdrawalResponse).get("data").get("id").asLong();

        mockMvc.perform(get("/api/tutors/me/earnings").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalanceAmount").value(35.00))
                .andExpect(jsonPath("$.data.pendingWithdrawalAmount").value(50.00));

        String adminToken = createAdminAndLogin("mc-wd-admin@example.com");
        // Reject -> reverse the hold -> balance back to 85.
        mockMvc.perform(patch("/api/admin/withdrawals/%d/reject".formatted(withdrawalId))
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Invalid bank info\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/tutors/me/earnings").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalanceAmount").value(85.00))
                .andExpect(jsonPath("$.data.pendingWithdrawalAmount").value(0.00));

        // The withdrawal also appears in the tutor's own list.
        mockMvc.perform(get("/api/tutors/me/withdrawals").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawals[0].status").value("REJECTED"));
    }

    @Test
    void insufficientBalanceWithdrawalIsRejected() throws Exception {
        String tutorToken = signupAndGetAccessToken("mc-wd2-tutor@example.com");
        createApprovedTutor(tutorToken, "mc-wd2");

        mockMvc.perform(post("/api/tutors/me/withdrawals")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"DOMESTIC_BANK\",\"amount\":10.00,\"payoutAccount\":\"123-456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // --- Promo waiver: 0% platform fee + 5% withdrawal payback ------------

    @Test
    void promoWaiverGivesZeroPlatformFeeAndWithdrawalPaybackOnPaid() throws Exception {
        String tutorToken = signupAndGetAccessToken("mc-promo-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "mc-promo");
        String adminToken = createAdminAndLogin("mc-promo-admin@example.com");

        // Grant promo waiver.
        mockMvc.perform(post("/api/admin/tutors/%d/promo-waiver".formatted(tutorProfileId))
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Initial 10-tutor promo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.granted").value(true))
                .andExpect(jsonPath("$.data.tutorProfileId").value(tutorProfileId));

        // Earning now settles at 0% platform fee: gross 100 -> net 100.
        seedTutorEarning(tutorProfileId, new BigDecimal("100.00"));
        mockMvc.perform(get("/api/tutors/me/earnings").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalanceAmount").value(100.00));

        // Withdraw 100 via DOMESTIC_BANK: promo fee fixed 5% -> fee 5, net 95, payback pending.
        String wr = mockMvc.perform(post("/api/tutors/me/withdrawals")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"DOMESTIC_BANK\",\"amount\":100.00,\"payoutAccount\":\"110-222-333\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeRate").value(0.05))
                .andExpect(jsonPath("$.data.feeAmount").value(5.00))
                .andExpect(jsonPath("$.data.promoFeeWaived").value(true))
                .andExpect(jsonPath("$.data.promoPaybackPending").value(true))
                .andExpect(jsonPath("$.data.promoPaybackAmount").value(5.00))
                .andReturn().getResponse().getContentAsString();
        long withdrawalId = objectMapper.readTree(wr).get("data").get("id").asLong();

        // After hold, balance = 0.
        mockMvc.perform(get("/api/tutors/me/earnings").header("Authorization", auth(tutorToken)))
                .andExpect(jsonPath("$.data.availableBalanceAmount").value(0.00));

        mockMvc.perform(patch("/api/admin/withdrawals/%d/approve".formatted(withdrawalId))
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // Paid -> promo 5% payback credited as ADJUSTMENT -> balance 5.00, pending cleared.
        mockMvc.perform(patch("/api/admin/withdrawals/%d/paid".formatted(withdrawalId))
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payoutReference\":\"TXN-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.promoPaybackPending").value(false))
                .andExpect(jsonPath("$.data.payoutAccount").value("TXN-123"));

        mockMvc.perform(get("/api/tutors/me/earnings").header("Authorization", auth(tutorToken)))
                .andExpect(jsonPath("$.data.availableBalanceAmount").value(5.00));

        // Revoke is allowed.
        mockMvc.perform(delete("/api/admin/tutors/%d/promo-waiver".formatted(tutorProfileId))
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
    }

    // --- Admin fee settings GET/PUT ---------------------------------------

    @Test
    void adminCanReadAndUpdateFeeSettings() throws Exception {
        String adminToken = createAdminAndLogin("mc-settings-admin@example.com");

        mockMvc.perform(get("/api/admin/settings/fees").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.studentFeeRate").value(0.10))
                .andExpect(jsonPath("$.data.platformFeeRate").value(0.15))
                .andExpect(jsonPath("$.data.cancellationCutoffHours").value(3))
                .andExpect(jsonPath("$.data.withdrawalFeeRatePayoneer").value(0.03))
                .andExpect(jsonPath("$.data.withdrawalFeeRateDomestic").value(0.05));

        mockMvc.perform(put("/api/admin/settings/fees")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platformFeeRate\":0.20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.platformFeeRate").value(0.20))
                .andExpect(jsonPath("$.data.studentFeeRate").value(0.10));
    }

    // --- Security: /api/tutors/me/** requires TUTOR role ------------------

    @Test
    void nonTutorCannotAccessTutorEarnings() throws Exception {
        String studentToken = signupAndGetAccessToken("mc-sec-student@example.com");
        mockMvc.perform(get("/api/tutors/me/earnings").header("Authorization", auth(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/credits/me").header("Authorization", auth(studentToken)))
                .andExpect(status().isOk());
        // Admin endpoints reject a non-admin.
        mockMvc.perform(get("/api/admin/settings/fees").header("Authorization", auth(studentToken)))
                .andExpect(status().isForbidden());
    }

    // --- helpers ----------------------------------------------------------

    /**
     * Seed an available tutor balance equal to the net of a lesson earning. The
     * platform fee mirrors production: 0% when a promo waiver is active for the
     * tutor, otherwise 15%. Credited as a positive ADJUSTMENT (a real, public
     * balance-credit path) with a unique synthetic key so it is never deduplicated.
     */
    private void seedTutorEarning(long tutorProfileId, BigDecimal grossUsd) {
        BigDecimal platformFee = grossUsd.multiply(effectivePlatformFeeRate(tutorProfileId))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal net = grossUsd.subtract(platformFee);
        settlementService.creditPromoPayback(tutorProfileId, seedKey.decrementAndGet(), net, Instant.now());
    }

    // Static so seed idempotency keys stay unique across test methods (each JUnit
    // method gets a fresh instance, but they share the same in-memory DB).
    private static final java.util.concurrent.atomic.AtomicLong seedKey = new java.util.concurrent.atomic.AtomicLong(-1_000_000L);

    private BigDecimal effectivePlatformFeeRate(long tutorProfileId) {
        return promoActive(tutorProfileId) ? BigDecimal.ZERO : new BigDecimal("0.15");
    }

    private boolean promoActive(long tutorProfileId) {
        return promoFeeWaiverGrantRepository.findByTutorProfileId(tutorProfileId)
                .map(g -> g.isEffective(java.time.LocalDate.now(java.time.ZoneOffset.UTC)))
                .orElse(false);
    }

    @Autowired
    com.haru.settlement.infra.PromoFeeWaiverGrantRepository promoFeeWaiverGrantRepository;

    private long createApprovedTutor(String tutorToken, String slug) throws Exception {
        long tutorProfileId = switchToTutorAndGetProfileId(tutorToken);
        saveCompleteTutorProfile(tutorToken);
        mockMvc.perform(post("/api/tutors/me/profile/submit").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk());
        String adminToken = createAdminAndLogin("approve-%s-admin@example.com".formatted(slug));
        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        return tutorProfileId;
    }

    private long switchToTutorAndGetProfileId(String accessToken) throws Exception {
        String response = mockMvc.perform(post("/api/tutors/me/switch").header("Authorization", auth(accessToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    private void saveCompleteTutorProfile(String accessToken) throws Exception {
        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Money Core Expert",
                                  "shortIntroduction": "Structured Korean lessons for global learners.",
                                  "aboutMe": "I help students build Korean confidence.",
                                  "whatIOffer": "Conversation, reading, and workplace Korean.",
                                  "category": "KOREAN",
                                  "profileImageUrl": "https://example.com/profile.jpg",
                                  "introVideoUrl": "https://youtu.be/haru-mc",
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

    private long createPaidCheckout(String studentToken, long tutorProfileId, int packCount) throws Exception {
        String response = mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorProfileId": %d,
                                  "lessonDurationMinutes": 25,
                                  "lessonPackCount": %d,
                                  "paymentMethod": "LEMON_SQUEEZY"
                                }
                                """.formatted(tutorProfileId, packCount)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long paymentId = objectMapper.readTree(response).get("data").get("id").asLong();
        // mock-paid-checkout-enabled => already PAID in test profile.
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        if (payment.getStatus() != com.haru.payment.domain.PaymentStatus.PAID) {
            payment.markPaid();
            paymentRepository.save(payment);
        }
        return paymentId;
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
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("accessToken").asText();
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
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("accessToken").asText();
    }

    private String auth(String accessToken) {
        return "Bearer " + accessToken;
    }
}
