package com.haru.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.booking.domain.Booking;
import com.haru.booking.domain.BookingCompletionState;
import com.haru.booking.infra.BookingRepository;
import com.haru.credit.application.CreditService;
import com.haru.payment.application.PaymentRefundService;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentMethod;
import com.haru.payment.domain.PaymentStatus;
import com.haru.payment.infra.PaymentRepository;
import com.haru.schedule.domain.TutorScheduleSlot;
import com.haru.schedule.infra.TutorScheduleSlotRepository;
import com.haru.settings.application.PlatformSettingsService;
import com.haru.settings.domain.FeePolicy;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for refund concurrency (#4), the provider real-refund guard
 * (#6), and webhook validation / idempotency (#8). Spring context + H2 + Flyway.
 *
 * <p>The concurrency case exercises a real two-thread race against two paid
 * payments for the same student where only one of the two purchased lessons is
 * still unused. Without the per-student credit-account write lock both refunds
 * would read the same stale "1 unused" snapshot and over-refund (credit 2
 * lessons). The lock serializes them, so the second refund recomputes against the
 * first's committed REFUNDED state and refunds 0 — total credit stays at exactly
 * the one truly-unused lesson.</p>
 */
@SpringBootTest(properties = "haru.payments.lemon-squeezy.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentRefundConcurrencyAndWebhookTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired TutorProfileRepository tutorProfileRepository;
    @Autowired TutorScheduleSlotRepository scheduleSlotRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRefundService paymentRefundService;
    @Autowired CreditService creditService;
    @Autowired PlatformSettingsService platformSettingsService;
    @Autowired TransactionTemplate txTemplate;

    // --- #4 refund concurrency: parallel approvals never over-refund ----------

    @Test
    void concurrentRefundApprovalsForSameStudentNeverOverRefund() throws Exception {
        Fixture f = newFixture("refund-race");

        // Two separate 1-packs @100 -> each discounted 100.00. Both PAID.
        Payment packA = savePaidPayment(f, 1, new BigDecimal("100.00"), Instant.now().minusSeconds(120));
        Payment packB = savePaidPayment(f, 1, new BigDecimal("100.00"), Instant.now().minusSeconds(60));

        // The student has consumed exactly ONE lesson (a completed booking). So of
        // the 2 purchased lessons, only 1 is unused/refundable in total.
        saveCompletedBooking(f, slot(f, 0));

        // Fire both refund approvals at the same instant.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> errA = new AtomicReference<>();
        AtomicReference<Throwable> errB = new AtomicReference<>();

        Runnable refundA = () -> {
            try {
                start.await();
                paymentRefundService.approveRefund(packA.getId());
            } catch (Throwable t) {
                errA.set(t);
            }
        };
        Runnable refundB = () -> {
            try {
                start.await();
                paymentRefundService.approveRefund(packB.getId());
            } catch (Throwable t) {
                errB.set(t);
            }
        };
        pool.submit(refundA);
        pool.submit(refundB);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // At least one must succeed; if either threw it must be a benign
        // "already refunded" / serialization conflict, never a crash.
        // The hard invariant is the credit total below.

        // CRITICAL invariant (#4): total refund credit issued == exactly 1 unused
        // lesson (100.00), never 2 (200.00). Serialization prevents double-spending
        // the same unused-lesson budget across the two payments.
        BigDecimal balance = creditService.currentBalance(f.studentUserId);
        assertThat(balance)
                .as("concurrent refunds credited %s (errA=%s, errB=%s)", balance, errA.get(), errB.get())
                .isEqualByComparingTo("100.00");

        // And the two payments combined moved to REFUNDED for at most the unused
        // budget: exactly one payment carries the 100.00 credit, the other 0.00.
        Payment a = paymentRepository.findById(packA.getId()).orElseThrow();
        Payment b = paymentRepository.findById(packB.getId()).orElseThrow();
        BigDecimal creditedA = a.getRefundCreditAmountUsd() == null ? BigDecimal.ZERO.setScale(2) : a.getRefundCreditAmountUsd();
        BigDecimal creditedB = b.getRefundCreditAmountUsd() == null ? BigDecimal.ZERO.setScale(2) : b.getRefundCreditAmountUsd();
        assertThat(creditedA.add(creditedB)).isEqualByComparingTo("100.00");
    }

    // --- #6 provider real refund issues NO Haru credit ------------------------

    @Test
    void providerRealRefundWebhookMarksRefundedWithoutIssuingCredit() throws Exception {
        Fixture f = newFixture("provider-refund");
        Payment paid = savePaidPayment(f, 1, new BigDecimal("100.00"), Instant.now());

        String payload = orderRefundedPayload(paid.getId(), "55501", "order-55501");
        mockMvc.perform(post("/api/payments/webhooks/lemon-squeezy")
                        .header("X-Signature", hmac(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Payment after = paymentRepository.findById(paid.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // Real (cash) refund -> NO Haru credit, and no credit-refund bookkeeping.
        assertThat(after.getRefundCreditAmountUsd()).isNull();
        assertThat(creditService.currentBalance(f.studentUserId)).isEqualByComparingTo("0.00");
    }

    @Test
    void creditRefundedPaymentIsNotReStampedByProviderRefundWebhook() throws Exception {
        Fixture f = newFixture("credit-then-provider");
        Payment paid = savePaidPayment(f, 1, new BigDecimal("100.00"), Instant.now());

        // Admin credit refund first: issues 100.00 credit, payment -> REFUNDED.
        paymentRefundService.approveRefund(paid.getId());
        assertThat(creditService.currentBalance(f.studentUserId)).isEqualByComparingTo("100.00");
        Payment afterCredit = paymentRepository.findById(paid.getId()).orElseThrow();
        assertThat(afterCredit.getRefundCreditAmountUsd()).isEqualByComparingTo("100.00");

        // A late provider order_refunded webhook for the same payment is a no-op
        // (already REFUNDED): credit unchanged, the credit bookkeeping preserved.
        String payload = orderRefundedPayload(paid.getId(), "77701", "order-77701");
        mockMvc.perform(post("/api/payments/webhooks/lemon-squeezy")
                        .header("X-Signature", hmac(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Payment afterWebhook = paymentRepository.findById(paid.getId()).orElseThrow();
        assertThat(afterWebhook.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // markRefundedByProvider was a no-op: the credit-refund amount survives and
        // the credit balance was NOT doubled.
        assertThat(afterWebhook.getRefundCreditAmountUsd()).isEqualByComparingTo("100.00");
        assertThat(creditService.currentBalance(f.studentUserId)).isEqualByComparingTo("100.00");
    }

    // --- #8 webhook validation: amount mismatch + PAID idempotency ------------

    @Test
    void webhookWithMismatchedTotalIsRejected() throws Exception {
        Fixture f = newFixture("wh-total");
        // 1-pack @100 -> total 110.00 -> 11000 minor units expected.
        Payment pending = savePendingPayment(f, 1, new BigDecimal("100.00"));

        String tampered = orderPaidPayloadWithTotalCurrency(pending.getId(), "60001", "order-60001", 9999L, "USD");
        mockMvc.perform(post("/api/payments/webhooks/lemon-squeezy")
                        .header("X-Signature", hmac(tampered))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tampered))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        // The mismatched event did NOT mark the payment paid.
        assertThat(paymentRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void webhookWithMismatchedCurrencyIsRejected() throws Exception {
        Fixture f = newFixture("wh-currency");
        Payment pending = savePendingPayment(f, 1, new BigDecimal("100.00"));

        // Correct total (11000) but wrong currency.
        String wrongCurrency = orderPaidPayloadWithTotalCurrency(pending.getId(), "60011", "order-60011", 11000L, "EUR");
        mockMvc.perform(post("/api/payments/webhooks/lemon-squeezy")
                        .header("X-Signature", hmac(wrongCurrency))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongCurrency))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        assertThat(paymentRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void webhookWithMatchingTotalAndCurrencyMarksPaidAndReplayIsIdempotent() throws Exception {
        Fixture f = newFixture("wh-replay");
        Payment pending = savePendingPayment(f, 1, new BigDecimal("100.00"));
        Instant updatedBefore;
        {
            Payment before = paymentRepository.findById(pending.getId()).orElseThrow();
            updatedBefore = before.getUpdatedAt();
        }

        // Correct total 11000 + USD -> marks PAID.
        String paidPayload = orderPaidPayloadWithTotalCurrency(pending.getId(), "60021", "order-60021", 11000L, "USD");
        mockMvc.perform(post("/api/payments/webhooks/lemon-squeezy")
                        .header("X-Signature", hmac(paidPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paidPayload))
                .andExpect(status().isOk());

        Payment afterFirst = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(afterFirst.getProviderOrderId()).isEqualTo("60021");
        Instant updatedAfterFirst = afterFirst.getUpdatedAt();
        assertThat(updatedAfterFirst).isAfterOrEqualTo(updatedBefore);

        // Replay the SAME order_created event: must be a no-op (no second markPaid).
        // We re-deliver with a DIFFERENT order id to prove the second mark is skipped
        // (the original provider order id is preserved, not overwritten).
        String replay = orderPaidPayloadWithTotalCurrency(pending.getId(), "99999", "order-99999", 11000L, "USD");
        mockMvc.perform(post("/api/payments/webhooks/lemon-squeezy")
                        .header("X-Signature", hmac(replay))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replay))
                .andExpect(status().isOk());

        Payment afterReplay = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(afterReplay.getStatus()).isEqualTo(PaymentStatus.PAID);
        // Idempotent: the already-PAID payment was untouched -> provider order id is
        // still the FIRST event's id, not the replay's.
        assertThat(afterReplay.getProviderOrderId()).isEqualTo("60021");
    }

    // --- payloads -------------------------------------------------------------

    private String orderPaidPayloadWithTotalCurrency(long paymentId, String orderId, String identifier, long totalMinor, String currency) {
        return """
                {
                  "meta": {
                    "event_name": "order_created",
                    "custom_data": { "payment_id": "%d" }
                  },
                  "data": {
                    "type": "orders",
                    "id": "%s",
                    "attributes": {
                      "identifier": "%s",
                      "status": "paid",
                      "refunded": false,
                      "total": %d,
                      "currency": "%s"
                    }
                  }
                }
                """.formatted(paymentId, orderId, identifier, totalMinor, currency);
    }

    private String orderRefundedPayload(long paymentId, String orderId, String identifier) {
        return """
                {
                  "meta": {
                    "event_name": "order_refunded",
                    "custom_data": { "payment_id": "%d" }
                  },
                  "data": {
                    "type": "orders",
                    "id": "%s",
                    "attributes": {
                      "identifier": "%s",
                      "status": "refunded",
                      "refunded": true
                    }
                  }
                }
                """.formatted(paymentId, orderId, identifier);
    }

    // --- persistence helpers --------------------------------------------------

    private Payment savePaidPayment(Fixture f, int packCount, BigDecimal unit, Instant createdAt) {
        FeePolicy policy = platformSettingsService.currentFeePolicy();
        return txTemplate.execute(s -> {
            UserAccount student = userAccountRepository.findById(f.studentUserId).orElseThrow();
            TutorProfile tutor = tutorProfileRepository.findById(f.tutorProfileId).orElseThrow();
            Payment payment = Payment.checkout(student, tutor, 25, packCount, unit, PaymentMethod.LEMON_SQUEEZY, policy);
            setField(payment, "createdAt", createdAt);
            setField(payment, "updatedAt", createdAt);
            payment.markPaid();
            return paymentRepository.saveAndFlush(payment);
        });
    }

    private Payment savePendingPayment(Fixture f, int packCount, BigDecimal unit) {
        FeePolicy policy = platformSettingsService.currentFeePolicy();
        return txTemplate.execute(s -> {
            UserAccount student = userAccountRepository.findById(f.studentUserId).orElseThrow();
            TutorProfile tutor = tutorProfileRepository.findById(f.tutorProfileId).orElseThrow();
            Payment payment = Payment.checkout(student, tutor, 25, packCount, unit, PaymentMethod.LEMON_SQUEEZY, policy);
            return paymentRepository.saveAndFlush(payment);
        });
    }

    private Booking saveCompletedBooking(Fixture f, TutorScheduleSlot slot) {
        return txTemplate.execute(s -> {
            UserAccount student = userAccountRepository.findById(f.studentUserId).orElseThrow();
            TutorProfile tutor = tutorProfileRepository.findById(f.tutorProfileId).orElseThrow();
            TutorScheduleSlot managedSlot = scheduleSlotRepository.findById(slot.getId()).orElseThrow();
            Booking booking = Booking.reserve(student, tutor, managedSlot, 25);
            booking.markCompleted(Instant.now());
            setField(booking, "completionState", BookingCompletionState.COMPLETED);
            return bookingRepository.saveAndFlush(booking);
        });
    }

    private TutorScheduleSlot slot(Fixture f, int index) {
        return f.slots.get(index);
    }

    private Fixture newFixture(String slug) {
        try {
            String tutorToken = signupAndGetAccessToken(slug + "-tutor@example.com");
            long tutorProfileId = switchToTutorAndGetProfileId(tutorToken);
            saveCompleteTutorProfile(tutorToken);
            mockMvc.perform(post("/api/tutors/me/profile/submit").header("Authorization", auth(tutorToken)))
                    .andExpect(status().isOk());
            String adminToken = createAdminAndLogin(slug + "-admin@example.com");
            mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                            .header("Authorization", auth(adminToken)))
                    .andExpect(status().isOk());

            String studentEmail = slug + "-student@example.com";
            signupAndGetAccessToken(studentEmail);
            long studentUserId = userAccountRepository.findByEmail(studentEmail).orElseThrow().getId();

            List<TutorScheduleSlot> slots = txTemplate.execute(s -> {
                TutorProfile tutor = tutorProfileRepository.findById(tutorProfileId).orElseThrow();
                Instant start = Instant.parse("2030-01-01T00:00:00Z");
                java.util.List<TutorScheduleSlot> created = new java.util.ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    Instant slotStart = start.plus(Duration.ofHours(i));
                    created.add(scheduleSlotRepository.saveAndFlush(
                            TutorScheduleSlot.of(tutor, slotStart, slotStart.plus(Duration.ofMinutes(25)))));
                }
                return created;
            });

            Fixture f = new Fixture();
            f.tutorProfileId = tutorProfileId;
            f.studentUserId = studentUserId;
            f.slots = slots;
            return f;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String field, Object value) {
        try {
            java.lang.reflect.Field declared = target.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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
                                  "displayName": "Refund Webhook Expert",
                                  "shortIntroduction": "Structured Korean lessons for global learners.",
                                  "aboutMe": "I help students build Korean confidence.",
                                  "whatIOffer": "Conversation, reading, and workplace Korean.",
                                  "category": "KOREAN",
                                  "profileImageUrl": "https://example.com/profile.jpg",
                                  "introVideoUrl": "https://youtu.be/haru-refund",
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

    private static final class Fixture {
        long tutorProfileId;
        long studentUserId;
        List<TutorScheduleSlot> slots;
    }
}
