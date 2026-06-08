package com.haru.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.booking.domain.Booking;
import com.haru.booking.domain.BookingCompletionState;
import com.haru.booking.infra.BookingRepository;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentMethod;
import com.haru.payment.domain.PaymentStatus;
import com.haru.payment.infra.PaymentRepository;
import com.haru.schedule.domain.TutorScheduleSlot;
import com.haru.schedule.infra.TutorScheduleSlotRepository;
import com.haru.settings.application.PlatformSettingsService;
import com.haru.settings.domain.FeePolicy;
import com.haru.settlement.application.SettlementJob;
import com.haru.settlement.application.SettlementService;
import com.haru.settlement.domain.EarningEntryType;
import com.haru.settlement.domain.TutorEarningLedger;
import com.haru.settlement.infra.TutorEarningLedgerRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the slot-based FIFO settlement (defects #2, #3, #5).
 * Spring context + H2 + Flyway (incl. V27 unique constraint). The tutor/student
 * are built through the real HTTP flow; payments are persisted directly so we can
 * control unit prices and creation order precisely for the mixed-pack scenario.
 * Settlement runs through the real {@link SettlementService#settleEarnedBooking}.
 */
@SpringBootTest(properties = "haru.payments.lemon-squeezy.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementSlotFifoIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired TutorProfileRepository tutorProfileRepository;
    @Autowired TutorScheduleSlotRepository scheduleSlotRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired TutorEarningLedgerRepository ledgerRepository;
    @Autowired SettlementService settlementService;
    @Autowired SettlementJob settlementJob;
    @Autowired PlatformSettingsService platformSettingsService;
    @Autowired TransactionTemplate txTemplate;

    // --- #1 critical: single pack net = gross * 0.85 --------------------------

    @Test
    void singlePackSettlesNetEqualsGrossTimesPointEightFive() {
        Fixture f = newFixture("slot-single");
        // 1-pack @100 -> discounted = 100.00 (no discount). net = gross * (1 - platformFeeRate).
        Payment payment = savePayment(f, 1, new BigDecimal("100.00"), Instant.now());
        Booking booking = saveCompletedBooking(f, slot(f, 0));

        // Read the live platform fee rate (other tests in the suite may have changed
        // it via the admin settings endpoint — the assertion stays order-independent).
        BigDecimal feeRate = platformSettingsService.currentFeePolicy().platformFeeRate();
        BigDecimal expectedFee = new BigDecimal("100.00").multiply(feeRate).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedNet = new BigDecimal("100.00").subtract(expectedFee);

        BigDecimal net = settleInTx(booking.getId(), EarningEntryType.LESSON_EARNED).orElseThrow();

        // Core invariant: net == gross * (1 - platformFeeRate); gross == pack discounted.
        assertThat(net).isEqualByComparingTo(expectedNet);
        TutorEarningLedger row = ledgerRepository
                .findFirstByBookingId(booking.getId()).orElseThrow();
        assertThat(row.getGrossAmountUsd()).isEqualByComparingTo("100.00");
        assertThat(row.getPlatformFeeAmountUsd()).isEqualByComparingTo(expectedFee);
        assertThat(row.getNetAmountUsd()).isEqualByComparingTo(expectedNet);
        assertThat(payment.getDiscountedAmount()).isEqualByComparingTo("100.00");
    }

    // --- #2 mixed-unit-price 2-pack FIFO attribution + #3 invariant -----------

    @Test
    void mixedUnitPricePacksAttributeGrossFifoAndSumEqualsDiscounted() {
        Fixture f = newFixture("slot-mixed");
        Instant base = Instant.now();
        // Pack A: oldest, 1-pack @100 -> discounted 100.00, 1 slot [100.00].
        Payment packA = savePayment(f, 1, new BigDecimal("100.00"), base.minusSeconds(120));
        // Pack B: newer, 5-pack @33.33 -> discounted 158.32,
        //   slots (largest-remainder) = [31.67, 31.67, 31.66, 31.66, 31.66].
        Payment packB = savePayment(f, 5, new BigDecimal("33.33"), base.minusSeconds(60));

        assertThat(packA.getDiscountedAmount()).isEqualByComparingTo("100.00");
        assertThat(packB.getDiscountedAmount()).isEqualByComparingTo("158.32");

        // Concatenated FIFO slot list = [100.00, 31.67, 31.67, 31.66, 31.66, 31.66].
        BigDecimal[] expectedGross = {
                new BigDecimal("100.00"),
                new BigDecimal("31.67"),
                new BigDecimal("31.67"),
                new BigDecimal("31.66"),
                new BigDecimal("31.66"),
                new BigDecimal("31.66"),
        };

        // 6 completed bookings, startAt ascending -> consume slots 0..5 in order.
        BigDecimal sumGross = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < 6; i++) {
            Booking booking = saveCompletedBooking(f, slot(f, i));
            settleInTx(booking.getId(), EarningEntryType.LESSON_EARNED);
            TutorEarningLedger row = ledgerRepository.findFirstByBookingId(booking.getId()).orElseThrow();
            assertThat(row.getGrossAmountUsd())
                    .as("booking %d (slot %d) gross", booking.getId(), i)
                    .isEqualByComparingTo(expectedGross[i]);
            sumGross = sumGross.add(row.getGrossAmountUsd());
        }

        // Invariant: Σ(per-lesson gross) == Σ(payment.discounted) = 100.00 + 158.32.
        BigDecimal sumDiscounted = packA.getDiscountedAmount().add(packB.getDiscountedAmount());
        assertThat(sumDiscounted).isEqualByComparingTo("258.32");
        assertThat(sumGross).isEqualByComparingTo(sumDiscounted);
    }

    // --- #5 same booking double-settle is blocked -----------------------------

    @Test
    void settlingSameBookingTwiceCreditsOnlyOnce() {
        Fixture f = newFixture("slot-dup");
        savePayment(f, 1, new BigDecimal("100.00"), Instant.now());
        Booking booking = saveCompletedBooking(f, slot(f, 0));

        BigDecimal feeRate = platformSettingsService.currentFeePolicy().platformFeeRate();
        BigDecimal expectedNet = new BigDecimal("100.00")
                .subtract(new BigDecimal("100.00").multiply(feeRate).setScale(2, java.math.RoundingMode.HALF_UP));

        BigDecimal firstNet = settleInTx(booking.getId(), EarningEntryType.LESSON_EARNED).orElseThrow();
        assertThat(firstNet).isEqualByComparingTo(expectedNet);

        // Second settle of the same booking is a no-op (read guard / settled flag).
        java.util.Optional<BigDecimal> secondNet = settleInTx(booking.getId(), EarningEntryType.LESSON_EARNED);
        assertThat(secondNet).isEmpty();

        // Exactly one LESSON_EARNED ledger row for the booking. Balance not doubled.
        List<TutorEarningLedger> rows = ledgerRepository.findAllByTutorProfileIdOrderByIdDesc(f.tutorProfileId).stream()
                .filter(r -> booking.getId().equals(r.getBookingId()))
                .toList();
        assertThat(rows).hasSize(1);
        // This tutor is brand new (unique fixture) so its only credit is this lesson.
        assertThat(settlementService.currentBalance(f.tutorProfileId)).isEqualByComparingTo(expectedNet);
    }

    // --- helpers --------------------------------------------------------------

    private java.util.Optional<BigDecimal> settleInTx(Long bookingId, EarningEntryType type) {
        return txTemplate.execute(s -> {
            Booking booking = bookingRepository.findWithDetailsById(bookingId).orElseThrow();
            return settlementService.settleEarnedBooking(booking, type, Instant.now());
        });
    }

    private Payment savePayment(Fixture f, int packCount, BigDecimal unit, Instant createdAt) {
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

    /** A persisted, COMPLETED booking on the given slot, ready to be settled. */
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

            // Persist 6 schedule slots, 1 hour apart, in the past (so settlement-ready).
            List<TutorScheduleSlot> slots = txTemplate.execute(s -> {
                TutorProfile tutor = tutorProfileRepository.findById(tutorProfileId).orElseThrow();
                Instant start = Instant.parse("2030-01-01T00:00:00Z");
                java.util.List<TutorScheduleSlot> created = new java.util.ArrayList<>();
                for (int i = 0; i < 6; i++) {
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
            java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
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
                                  "displayName": "Slot FIFO Expert",
                                  "shortIntroduction": "Structured Korean lessons for global learners.",
                                  "aboutMe": "I help students build Korean confidence.",
                                  "whatIOffer": "Conversation, reading, and workplace Korean.",
                                  "category": "KOREAN",
                                  "profileImageUrl": "https://example.com/profile.jpg",
                                  "introVideoUrl": "https://youtu.be/haru-slot",
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

    private static final class Fixture {
        long tutorProfileId;
        long studentUserId;
        List<TutorScheduleSlot> slots;
    }
}
