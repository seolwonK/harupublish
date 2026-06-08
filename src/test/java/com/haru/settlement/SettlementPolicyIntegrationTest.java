package com.haru.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.booking.domain.Booking;
import com.haru.booking.infra.BookingRepository;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentStatus;
import com.haru.payment.infra.PaymentRepository;
import com.haru.settlement.application.SettlementService;
import com.haru.settlement.domain.EarningEntryType;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Settlement pricing (FIFO pack matching) and cancel-actor policy
 * (tutor self-cancel must never accrue an earning).
 */
@SpringBootTest(properties = "haru.payments.lemon-squeezy.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementPolicyIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    SettlementService settlementService;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Mixed packs: 1-pack @ $100 (per-lesson 100.00) then 5-pack @ $475
     * discounted (per-lesson 95.00). The 1st settled lesson must draw from the
     * 1-pack and the 2nd from the 5-pack — not "first payment forever".
     */
    @Test
    void settlementMatchesLessonsToPacksFifo() throws Exception {
        String tutorToken = signupAndGetAccessToken("fifo-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "fifo");
        String studentToken = signupAndGetAccessToken("fifo-student@example.com");

        createPaidCheckout(studentToken, tutorProfileId, 1);  // per-lesson 100.00
        createPaidCheckout(studentToken, tutorProfileId, 5);  // per-lesson 95.00

        saveSchedule(tutorToken, "2031-07-01T00:00:00Z", "2031-07-01T01:00:00Z");
        long slot1 = scheduleSlotIdAt(tutorToken, "2031-07-01T00:00:00Z", "2031-07-01T02:00:00Z", 0);
        long slot2 = scheduleSlotIdAt(tutorToken, "2031-07-01T00:00:00Z", "2031-07-01T02:00:00Z", 1);
        long booking1 = createBooking(studentToken, tutorProfileId, slot1);
        long booking2 = createBooking(studentToken, tutorProfileId, slot2);

        settleInTx(booking1);
        settleInTx(booking2);

        // Gross is fee-rate independent (other tests may change platform settings):
        // 1st settled lesson draws from the 1-pack (100.00), 2nd from the 5-pack (95.00).
        var ledger = settlementService.ledgerFor(tutorProfileId); // newest first
        assertThat(ledger).hasSize(2);
        assertThat(ledger.get(1).getGrossAmountUsd()).isEqualByComparingTo("100.00");
        assertThat(ledger.get(0).getGrossAmountUsd()).isEqualByComparingTo("95.00");

        mockMvc.perform(get("/api/tutors/me/earnings").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledger[0].entryType").value("LESSON_EARNED"));
    }

    /**
     * A tutor cancelling their own lesson inside the cancel window must be a
     * NORMAL cancel: no NO_SHOW earning for the tutor and the student's credit
     * stays usable for re-booking.
     */
    @Test
    void tutorSelfCancelInsideWindowEarnsNothingAndRefundsCredit() throws Exception {
        String tutorToken = signupAndGetAccessToken("selfcancel-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "selfcancel");
        String studentToken = signupAndGetAccessToken("selfcancel-student@example.com");
        createPaidCheckout(studentToken, tutorProfileId, 1);

        // Two aligned slots inside the 3-hour cancel window.
        Instant slotStart1 = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant slotStart2 = slotStart1.plus(1, ChronoUnit.HOURS);
        saveSchedule(tutorToken, slotStart1.toString(), slotStart2.toString());
        String from = slotStart1.minus(1, ChronoUnit.HOURS).toString();
        String to = slotStart2.plus(1, ChronoUnit.HOURS).toString();
        long slot1 = scheduleSlotIdAt(tutorToken, from, to, 0);
        long slot2 = scheduleSlotIdAt(tutorToken, from, to, 1);

        long bookingId = createBooking(studentToken, tutorProfileId, slot1);

        // Tutor cancels their own lesson right before start.
        mockMvc.perform(patch("/api/bookings/%d/cancel".formatted(bookingId))
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Tutor unavailable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // No earning accrued to the tutor.
        Booking cancelled = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(cancelled.isSettled()).isFalse();
        assertThat(cancelled.tutorEarns()).isFalse();
        mockMvc.perform(get("/api/tutors/me/earnings").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEarnedAmount").value(0.00))
                .andExpect(jsonPath("$.data.availableBalanceAmount").value(0.00));

        // The student's lesson credit is back: re-booking another slot succeeds.
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorProfileId": %d,
                                  "scheduleSlotId": %d,
                                  "lessonDurationMinutes": 25
                                }
                                """.formatted(tutorProfileId, slot2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    /**
     * Reproduces the pre-V24 legacy state (CANCELLED booking with NULL
     * completion_state, which counts as "consumed" and blocks re-booking) and
     * verifies the V29 backfill statement frees the credit.
     */
    @Test
    void v29BackfillFreesLegacyCancelledBookingsWithNullCompletionState() throws Exception {
        String tutorToken = signupAndGetAccessToken("backfill-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "backfill");
        String studentToken = signupAndGetAccessToken("backfill-student@example.com");
        createPaidCheckout(studentToken, tutorProfileId, 1);

        saveSchedule(tutorToken, "2031-08-01T00:00:00Z", "2031-08-01T01:00:00Z");
        long slot1 = scheduleSlotIdAt(tutorToken, "2031-08-01T00:00:00Z", "2031-08-01T02:00:00Z", 0);
        long slot2 = scheduleSlotIdAt(tutorToken, "2031-08-01T00:00:00Z", "2031-08-01T02:00:00Z", 1);

        long bookingId = createBooking(studentToken, tutorProfileId, slot1);
        mockMvc.perform(patch("/api/bookings/%d/cancel".formatted(bookingId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"plans changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Simulate a legacy pre-V24 row: cancelled but completion_state NULL.
        jdbcTemplate.update("UPDATE bookings SET completion_state = NULL WHERE id = ?", bookingId);

        // Bug reproduced: the NULL row counts as consumed -> re-booking blocked.
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(tutorProfileId, slot2)))
                .andExpect(status().isBadRequest());

        // Apply the V29 backfill statement.
        jdbcTemplate.update("""
                UPDATE bookings
                SET completion_state = 'CANCELLED_NORMAL'
                WHERE status = 'CANCELLED'
                  AND completion_state IS NULL
                """);

        // Credit freed: re-booking succeeds.
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(tutorProfileId, slot2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    /**
     * A booking settled after its month's settlement was closed must roll its
     * amounts forward into the current month instead of silently vanishing
     * from the monthly reports.
     */
    @Test
    void lateSettlementAfterMonthClosedRollsForwardIntoCurrentMonth() throws Exception {
        String tutorToken = signupAndGetAccessToken("rollfwd-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "rollfwd");
        String studentToken = signupAndGetAccessToken("rollfwd-student@example.com");
        createPaidCheckout(studentToken, tutorProfileId, 5);

        // Two lessons in the PREVIOUS month (UTC).
        java.time.LocalDate prev = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusMonths(1).withDayOfMonth(5);
        Instant slotStart1 = prev.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant slotStart2 = slotStart1.plus(1, ChronoUnit.HOURS);
        saveSchedule(tutorToken, slotStart1.toString(), slotStart2.toString());
        String from = slotStart1.minus(1, ChronoUnit.HOURS).toString();
        String to = slotStart2.plus(1, ChronoUnit.HOURS).toString();
        long booking1 = createBooking(studentToken, tutorProfileId, scheduleSlotIdAt(tutorToken, from, to, 0));
        long booking2 = createBooking(studentToken, tutorProfileId, scheduleSlotIdAt(tutorToken, from, to, 1));

        // First settlement opens the previous month's rollup.
        settleInTx(booking1);
        String settlements = mockMvc.perform(get("/api/tutors/me/settlements").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var prevMonthNode = objectMapper.readTree(settlements).get("data").get("settlements").get(0);
        long prevSettlementId = prevMonthNode.get("id").asLong();
        assertThat(prevMonthNode.get("month").asInt()).isEqualTo(prev.getMonthValue());
        assertThat(prevMonthNode.get("lessonCount").asInt()).isEqualTo(1);

        // Admin closes the previous month, then a late booking settles.
        String adminToken = createAdminAndLogin("rollfwd-close-admin@example.com");
        mockMvc.perform(patch("/api/admin/settlements/%d/status".formatted(prevSettlementId))
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());
        settleInTx(booking2);

        // Closed month unchanged; the late lesson rolled into the current month.
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        String after = mockMvc.perform(get("/api/tutors/me/settlements").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var rows = objectMapper.readTree(after).get("data").get("settlements");
        assertThat(rows.size()).isEqualTo(2);
        boolean checkedPrev = false;
        boolean checkedCurrent = false;
        for (var row : rows) {
            int month = row.get("month").asInt();
            if (month == prev.getMonthValue()) {
                assertThat(row.get("lessonCount").asInt()).isEqualTo(1);
                assertThat(row.get("status").asText()).isEqualTo("CLOSED");
                checkedPrev = true;
            }
            if (month == today.getMonthValue() && row.get("year").asInt() == today.getYear()) {
                assertThat(row.get("lessonCount").asInt()).isEqualTo(1);
                assertThat(row.get("status").asText()).isEqualTo("OPEN");
                checkedCurrent = true;
            }
        }
        assertThat(checkedPrev).as("previous month settlement present").isTrue();
        assertThat(checkedCurrent).as("rolled-forward current month settlement present").isTrue();
    }

    private String bookingJson(long tutorProfileId, long scheduleSlotId) {
        return """
                {
                  "tutorProfileId": %d,
                  "scheduleSlotId": %d,
                  "lessonDurationMinutes": 25
                }
                """.formatted(tutorProfileId, scheduleSlotId);
    }

    private BigDecimal settleInTx(long bookingId) {
        return transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findById(bookingId).orElseThrow();
            return settlementService.settleEarnedBooking(booking, EarningEntryType.LESSON_EARNED, Instant.now())
                    .orElseThrow();
        });
    }

    private void saveSchedule(String tutorToken, String... startAts) throws Exception {
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < startAts.length; i++) {
            if (i > 0) slots.append(",");
            slots.append("{ \"startAt\": \"%s\" }".formatted(startAts[i]));
        }
        mockMvc.perform(put("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"slots\": [%s] }".formatted(slots)))
                .andExpect(status().isOk());
    }

    private long scheduleSlotIdAt(String tutorToken, String from, String to, int index) throws Exception {
        String response = mockMvc.perform(get("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorToken))
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("slots").get(index).get("id").asLong();
    }

    private long createBooking(String studentToken, long tutorProfileId, long scheduleSlotId) throws Exception {
        String response = mockMvc.perform(post("/api/bookings")
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
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
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
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        if (payment.getStatus() != PaymentStatus.PAID) {
            payment.markPaid();
            paymentRepository.save(payment);
        }
        return paymentId;
    }

    private long createApprovedTutor(String tutorToken, String slug) throws Exception {
        String switchResponse = mockMvc.perform(post("/api/tutors/me/switch").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long tutorProfileId = objectMapper.readTree(switchResponse).get("data").get("id").asLong();

        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Settlement Policy Tutor",
                                  "shortIntroduction": "Structured Korean lessons.",
                                  "aboutMe": "I help students build Korean confidence.",
                                  "whatIOffer": "Conversation and reading.",
                                  "category": "KOREAN",
                                  "profileImageUrl": "https://example.com/profile.jpg",
                                  "introVideoUrl": "https://youtu.be/haru-sp",
                                  "thumbnailUrl": "https://example.com/thumb.jpg",
                                  "availableLanguages": ["Korean", "English"],
                                  "lessonPrice25Amount": 100.00,
                                  "lessonPrice50Amount": 180.00,
                                  "availableTimeNote": "Weekday evenings KST",
                                  "paymentMethod": "PAYPAL"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tutors/me/profile/submit").header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk());

        String adminToken = createAdminAndLogin("sp-%s-admin@example.com".formatted(slug));
        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        return tutorProfileId;
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
