package com.haru.settlement.application;

import com.haru.booking.domain.Booking;
import com.haru.booking.infra.BookingRepository;
import com.haru.settlement.domain.EarningEntryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Periodically finalizes lessons whose time has passed and credits the tutor.
 *
 * <p>For each RESERVED booking past its {@code endAt} that has not been settled,
 * the booking is marked COMPLETED and the tutor is credited LESSON_EARNED. This
 * is the "cyber money is credited immediately on completion" rule. Late-cancel /
 * no-show bookings are normally settled synchronously in {@code BookingService};
 * the job also sweeps any CANCELLED_LATE bookings that were not yet credited as
 * a defensive backstop. Everything is idempotent via the {@code settled} flag.</p>
 *
 * <p><b>MVP limitation:</b> there is no attendance/join log yet, so a reserved
 * booking that simply expired cannot be distinguished between "completed" and a
 * genuine student no-show. We therefore credit all expired reserved lessons as
 * COMPLETED. Only explicit late cancels are recorded as NO_SHOW_EARNED. See
 * followups for the precise no-show tracking plan.</p>
 */
@Component
public class SettlementJob {

    private static final Logger log = LoggerFactory.getLogger(SettlementJob.class);

    private final BookingRepository bookingRepository;
    private final SettlementService settlementService;

    public SettlementJob(BookingRepository bookingRepository, SettlementService settlementService) {
        this.bookingRepository = bookingRepository;
        this.settlementService = settlementService;
    }

    /**
     * Runs every 5 minutes (after a 1-minute startup delay). Each due booking is
     * handled in its own transaction so one failure does not roll back the rest.
     */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    public void settleDueBookings() {
        Instant now = Instant.now();
        int completed = 0;
        int lateCancel = 0;

        List<Booking> reserved = bookingRepository.findReservedDueForSettlement(now);
        for (Booking booking : reserved) {
            try {
                settleCompletedBooking(booking.getId());
                completed++;
            } catch (RuntimeException exception) {
                log.warn("Settlement failed for completed booking {}: {}", booking.getId(), exception.getMessage());
            }
        }

        List<Booking> lateCancels = bookingRepository.findLateCancelDueForSettlement();
        for (Booking booking : lateCancels) {
            try {
                settleLateCancelBooking(booking.getId());
                lateCancel++;
            } catch (RuntimeException exception) {
                log.warn("Settlement failed for late-cancel booking {}: {}", booking.getId(), exception.getMessage());
            }
        }

        if (completed > 0 || lateCancel > 0) {
            log.info("Settlement job credited {} completed and {} late-cancel bookings.", completed, lateCancel);
        }
    }

    @Transactional
    public void settleCompletedBooking(Long bookingId) {
        Booking booking = bookingRepository.findWithDetailsById(bookingId).orElse(null);
        if (booking == null || booking.isSettled()) {
            return;
        }
        Instant now = Instant.now();
        booking.markCompleted(now);
        settlementService.settleEarnedBooking(booking, EarningEntryType.LESSON_EARNED, now);
    }

    @Transactional
    public void settleLateCancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findWithDetailsById(bookingId).orElse(null);
        if (booking == null || booking.isSettled()) {
            return;
        }
        settlementService.settleEarnedBooking(booking, EarningEntryType.NO_SHOW_EARNED, Instant.now());
    }
}
