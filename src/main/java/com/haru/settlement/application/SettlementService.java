package com.haru.settlement.application;

import com.haru.booking.domain.Booking;
import com.haru.booking.infra.BookingRepository;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentStatus;
import com.haru.payment.infra.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.haru.settings.application.PlatformSettingsService;
import com.haru.settings.domain.FeePolicy;
import com.haru.settlement.domain.EarningEntryType;
import com.haru.settlement.domain.MonthlySettlement;
import com.haru.settlement.domain.PromoFeeWaiverGrant;
import com.haru.settlement.domain.TutorEarningLedger;
import com.haru.settlement.infra.MonthlySettlementRepository;
import com.haru.settlement.infra.PromoFeeWaiverGrantRepository;
import com.haru.settlement.infra.TutorEarningLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Core settlement: turns an earned booking into tutor "cyber money" (USD).
 *
 * <p>Per money model 1, the per-lesson gross is the matching payment's
 * {@code discounted / packCount}; the tutor net = gross * (1 - platformFeeRate)
 * (default 15% platform fee), and the platform fee is the remainder. The gross
 * is matched to the booking by FIFO over the student's PAID payments for the
 * same (tutor, 25-minute) tuple — the same key {@code ensureRemainingLessonCredit}
 * uses on the booking side.</p>
 *
 * <p>Idempotency is enforced by the caller's {@code settled = false} guard plus
 * a defensive check against an existing ledger row for the booking. The running
 * balance is read under a pessimistic write lock so concurrent earnings on the
 * same tutor serialize.</p>
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final TutorEarningLedgerRepository tutorEarningLedgerRepository;
    private final MonthlySettlementRepository monthlySettlementRepository;
    private final PromoFeeWaiverGrantRepository promoFeeWaiverGrantRepository;
    private final PlatformSettingsService platformSettingsService;

    public SettlementService(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            TutorEarningLedgerRepository tutorEarningLedgerRepository,
            MonthlySettlementRepository monthlySettlementRepository,
            PromoFeeWaiverGrantRepository promoFeeWaiverGrantRepository,
            PlatformSettingsService platformSettingsService
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.tutorEarningLedgerRepository = tutorEarningLedgerRepository;
        this.monthlySettlementRepository = monthlySettlementRepository;
        this.promoFeeWaiverGrantRepository = promoFeeWaiverGrantRepository;
        this.platformSettingsService = platformSettingsService;
    }

    /**
     * Credit a tutor for one earned booking (completed, late-cancel, or no-show).
     * Idempotent: if the booking already has a ledger row, or is already settled,
     * this is a no-op. Returns the net USD credited (or {@link Optional#empty()}
     * when nothing was credited).
     */
    @Transactional
    public Optional<BigDecimal> settleEarnedBooking(Booking booking, EarningEntryType entryType, Instant now) {
        if (booking.isSettled()) {
            return Optional.empty();
        }
        Optional<TutorEarningLedger> existing = tutorEarningLedgerRepository.findFirstByBookingId(booking.getId());
        if (existing.isPresent()) {
            // Already credited in a prior run; just stamp the booking idempotently.
            booking.markSettled(existing.get().getNetAmountUsd(), now);
            return Optional.empty();
        }

        Long tutorProfileId = booking.getTutorProfile().getId();
        FeePolicy feePolicy = platformSettingsService.currentFeePolicy();
        // Promo-waiver tutors settle with a 0% platform fee. The waiver applies to
        // the platform (lesson) fee only — the withdrawal fee is handled separately.
        BigDecimal platformFeeRate = effectivePlatformFeeRate(tutorProfileId, feePolicy, booking.getEndAt());

        BigDecimal grossUsd = resolveGrossUsd(booking);
        BigDecimal platformFeeUsd = grossUsd.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netUsd = grossUsd.subtract(platformFeeUsd);

        BigDecimal previousBalance = tutorEarningLedgerRepository.findLatestForUpdate(tutorProfileId)
                .map(TutorEarningLedger::getBalanceAfterUsd)
                .orElse(BigDecimal.ZERO.setScale(2));

        TutorEarningLedger entry = TutorEarningLedger.lessonEarning(
                tutorProfileId,
                entryType,
                booking.getId(),
                grossUsd,
                platformFeeUsd,
                netUsd,
                previousBalance,
                platformFeeRate,
                memoFor(entryType, booking),
                now
        );
        tutorEarningLedgerRepository.save(entry);

        accumulateMonthlySettlement(tutorProfileId, grossUsd, platformFeeUsd, netUsd, booking, now);

        booking.markSettled(netUsd, now);
        return Optional.of(netUsd);
    }

    /**
     * Per-lesson tutor gross (USD) for a booking, matched FIFO to the student's
     * PAID payments for the same (tutor, duration) tuple: the n-th settled
     * lesson draws from the n-th unit across packs in purchase order, so mixed
     * pack prices ($20 1-pack + $18×5-pack) settle at the unit price of the
     * pack actually being consumed. Falls back to zero only when no payment is
     * found (defensive — booking creation requires paid credit).
     *
     * <p>If a partially-consumed pack is later refunded it drops out of the
     * PAID list and future lessons re-index against the remaining packs;
     * already-settled grosses stay locked in the ledger.</p>
     */
    private BigDecimal resolveGrossUsd(Booking booking) {
        List<Payment> paidPayments = paymentRepository
                .findAllByStudentIdAndTutorProfileIdAndLessonDurationMinutesAndStatusOrderByCreatedAtAsc(
                        booking.getStudent().getId(),
                        booking.getTutorProfile().getId(),
                        booking.getLessonDurationMinutes(),
                        PaymentStatus.PAID
                );
        if (paidPayments.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }

        // 0-based FIFO position of this lesson = lessons already settled for the
        // tuple (this booking is not yet marked settled at this point).
        long fifoIndex = bookingRepository.countSettledForTuple(
                booking.getStudent().getId(),
                booking.getTutorProfile().getId(),
                booking.getLessonDurationMinutes()
        );
        long cumulativeUnits = 0;
        for (Payment payment : paidPayments) {
            cumulativeUnits += payment.getLessonPackCount();
            if (fifoIndex < cumulativeUnits) {
                return payment.perLessonDiscountedAmount();
            }
        }
        // More settled lessons than paid units (should not happen) — price at
        // the most recent pack rather than failing the settlement.
        log.warn("FIFO index {} exceeds paid units {} for booking {}; using last pack price.",
                fifoIndex, cumulativeUnits, booking.getId());
        return paidPayments.get(paidPayments.size() - 1).perLessonDiscountedAmount();
    }

    private void accumulateMonthlySettlement(
            Long tutorProfileId,
            BigDecimal grossUsd,
            BigDecimal platformFeeUsd,
            BigDecimal netUsd,
            Booking booking,
            Instant now
    ) {
        LocalDate earnedOn = LocalDate.ofInstant(booking.getEndAt(), ZoneOffset.UTC);
        int year = earnedOn.getYear();
        int month = earnedOn.getMonthValue();

        MonthlySettlement settlement = monthlySettlementRepository.findForUpdate(tutorProfileId, year, month)
                .orElseGet(() -> monthlySettlementRepository.save(
                        MonthlySettlement.open(tutorProfileId, year, month, now)
                ));
        if (!settlement.isOpen()) {
            // The booking's month was already closed by an admin (late settlement
            // via the backstop job). Roll the amounts forward into the current
            // month so the monthly reports stay reconciled with the ledger.
            LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
            if (today.getYear() == year && today.getMonthValue() == month) {
                log.warn("Monthly settlement {}-{} for tutor {} is {} but booking {} settled now; "
                                + "amount written to ledger only.",
                        year, month, tutorProfileId, settlement.getStatus(), booking.getId());
                return;
            }
            settlement = monthlySettlementRepository.findForUpdate(tutorProfileId, today.getYear(), today.getMonthValue())
                    .orElseGet(() -> monthlySettlementRepository.save(
                            MonthlySettlement.open(tutorProfileId, today.getYear(), today.getMonthValue(), now)
                    ));
            if (!settlement.isOpen()) {
                log.warn("Current monthly settlement {}-{} for tutor {} is also {}; booking {} "
                                + "amount written to ledger only.",
                        today.getYear(), today.getMonthValue(), tutorProfileId, settlement.getStatus(), booking.getId());
                return;
            }
            log.info("Booking {} settled after {}-{} was closed; rolled forward into {}-{} for tutor {}.",
                    booking.getId(), year, month, today.getYear(), today.getMonthValue(), tutorProfileId);
        }
        settlement.addLesson(grossUsd, platformFeeUsd, netUsd, now);
    }

    private String memoFor(EarningEntryType entryType, Booking booking) {
        return switch (entryType) {
            case LESSON_EARNED -> "Lesson completed (booking #" + booking.getId() + ")";
            case NO_SHOW_EARNED -> "No-show / late cancel (booking #" + booking.getId() + ")";
            default -> "Earning (booking #" + booking.getId() + ")";
        };
    }

    /**
     * The platform fee rate that applies to this tutor's earnings, accounting for
     * an effective promo waiver (0%) on the earning date.
     */
    private BigDecimal effectivePlatformFeeRate(Long tutorProfileId, FeePolicy feePolicy, Instant earnedAtUtc) {
        boolean waived = promoFeeWaiverGrantRepository.findByTutorProfileId(tutorProfileId)
                .map(grant -> grant.isEffective(LocalDate.ofInstant(earnedAtUtc, ZoneOffset.UTC)))
                .orElse(false);
        return waived ? BigDecimal.ZERO.setScale(4) : feePolicy.platformFeeRate();
    }

    /**
     * Hold (debit, -) the withdrawal amount on the tutor ledger to prevent
     * double-withdrawal. Reads the latest balance under a write lock and rejects
     * the hold if the balance is insufficient.
     */
    @Transactional
    public void holdForWithdrawal(Long tutorProfileId, Long withdrawalId, BigDecimal amountUsd, Instant now) {
        BigDecimal previousBalance = lockedBalance(tutorProfileId);
        if (previousBalance.compareTo(amountUsd) < 0) {
            throw new com.haru.common.exception.BusinessException(
                    com.haru.common.exception.ErrorCode.INVALID_REQUEST,
                    "Insufficient balance for withdrawal."
            );
        }
        tutorEarningLedgerRepository.save(TutorEarningLedger.adjustment(
                tutorProfileId,
                EarningEntryType.WITHDRAWAL_HOLD,
                amountUsd.negate(),
                previousBalance,
                withdrawalId,
                "WITHDRAWAL_HOLD:" + withdrawalId,
                "Withdrawal hold (request #" + withdrawalId + ")",
                now
        ));
    }

    /** Reverse (re-credit, +) a held withdrawal amount when the request is rejected. */
    @Transactional
    public void reverseWithdrawalHold(Long tutorProfileId, Long withdrawalId, BigDecimal amountUsd, Instant now) {
        String key = "WITHDRAWAL_REVERSED:" + withdrawalId;
        if (tutorEarningLedgerRepository.existsByIdempotencyKey(key)) {
            return;
        }
        BigDecimal previousBalance = lockedBalance(tutorProfileId);
        tutorEarningLedgerRepository.save(TutorEarningLedger.adjustment(
                tutorProfileId,
                EarningEntryType.WITHDRAWAL_REVERSED,
                amountUsd,
                previousBalance,
                withdrawalId,
                key,
                "Withdrawal rejected, hold reversed (request #" + withdrawalId + ")",
                now
        ));
    }

    /**
     * Credit (+) a promo fee payback as an ADJUSTMENT. Idempotent on
     * unique(withdrawal_id, 'PAYBACK'). Returns true when a new entry was written.
     */
    @Transactional
    public boolean creditPromoPayback(Long tutorProfileId, Long withdrawalId, BigDecimal amountUsd, Instant now) {
        String key = "PAYBACK:" + withdrawalId;
        if (tutorEarningLedgerRepository.existsByIdempotencyKey(key)) {
            return false;
        }
        BigDecimal previousBalance = lockedBalance(tutorProfileId);
        tutorEarningLedgerRepository.save(TutorEarningLedger.adjustment(
                tutorProfileId,
                EarningEntryType.ADJUSTMENT,
                amountUsd,
                previousBalance,
                withdrawalId,
                key,
                "Promo withdrawal fee payback (request #" + withdrawalId + ")",
                now
        ));
        return true;
    }

    private BigDecimal lockedBalance(Long tutorProfileId) {
        return tutorEarningLedgerRepository.findLatestForUpdate(tutorProfileId)
                .map(TutorEarningLedger::getBalanceAfterUsd)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    @Transactional(readOnly = true)
    public BigDecimal currentBalance(Long tutorProfileId) {
        return tutorEarningLedgerRepository.findAllByTutorProfileIdOrderByIdDesc(tutorProfileId).stream()
                .findFirst()
                .map(TutorEarningLedger::getBalanceAfterUsd)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    @Transactional(readOnly = true)
    public List<TutorEarningLedger> ledgerFor(Long tutorProfileId) {
        return tutorEarningLedgerRepository.findAllByTutorProfileIdOrderByIdDesc(tutorProfileId);
    }

    /**
     * Aggregate earning totals for the tutor earnings view. Holds are netted
     * against their reversals so a rejected withdrawal contributes nothing to
     * "withdrawn"/"pending".
     */
    @Transactional(readOnly = true)
    public EarningTotals earningTotals(Long tutorProfileId) {
        List<TutorEarningLedger> ledger = tutorEarningLedgerRepository.findAllByTutorProfileIdOrderByIdDesc(tutorProfileId);
        BigDecimal totalEarned = BigDecimal.ZERO.setScale(2);
        BigDecimal holds = BigDecimal.ZERO.setScale(2);     // sum of |hold| amounts
        BigDecimal reversed = BigDecimal.ZERO.setScale(2);  // sum of reversed amounts
        BigDecimal balance = ledger.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : ledger.get(0).getBalanceAfterUsd();
        for (TutorEarningLedger entry : ledger) {
            switch (entry.getEntryType()) {
                case LESSON_EARNED, NO_SHOW_EARNED, ADJUSTMENT -> {
                    if (entry.getNetAmountUsd().signum() > 0) {
                        totalEarned = totalEarned.add(entry.getNetAmountUsd());
                    }
                }
                case WITHDRAWAL_HOLD -> holds = holds.add(entry.getNetAmountUsd().abs());
                case WITHDRAWAL_REVERSED -> reversed = reversed.add(entry.getNetAmountUsd());
            }
        }
        // Net of holds that were not reversed = funds that left / are leaving.
        BigDecimal netHeld = holds.subtract(reversed);
        if (netHeld.signum() < 0) {
            netHeld = BigDecimal.ZERO.setScale(2);
        }
        return new EarningTotals(balance, totalEarned, netHeld, netHeld);
    }

    /** Aggregate earning totals (USD). */
    public record EarningTotals(
            BigDecimal availableBalance,
            BigDecimal totalEarned,
            BigDecimal totalWithdrawn,
            BigDecimal pendingWithdrawal
    ) {
    }

    @Transactional(readOnly = true)
    public List<MonthlySettlement> settlementsFor(Long tutorProfileId) {
        return monthlySettlementRepository.findAllByTutorProfileIdOrderBySettlementYearDescSettlementMonthDesc(tutorProfileId);
    }

    @Transactional
    public MonthlySettlement updateSettlementStatus(Long settlementId, com.haru.settlement.domain.MonthlySettlementStatus target) {
        MonthlySettlement settlement = monthlySettlementRepository.findById(settlementId)
                .orElseThrow(() -> new com.haru.common.exception.NotFoundException("Monthly settlement was not found."));
        settlement.changeStatus(target, Instant.now());
        return settlement;
    }
}
