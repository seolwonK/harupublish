package com.haru.payment.application;

import com.haru.booking.infra.BookingRepository;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.NotFoundException;
import com.haru.credit.application.CreditService;
import com.haru.credit.domain.HaruCreditAccount;
import com.haru.credit.infra.HaruCreditAccountRepository;
import com.haru.payment.api.dto.PaymentResponse;
import com.haru.payment.api.dto.RefundRequestResponse;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentStatus;
import com.haru.payment.infra.PaymentRepository;
import com.haru.settings.application.PlatformSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Admin refund approval -> Haru credit issuance (E2E).
 *
 * <p>A refund only ever returns <b>unused</b> lesson value as Haru credit (no
 * cash-out). The refundable units for a payment are the still-unused lessons for
 * its (student, tutor, 25-minute) tuple, capped at the payment's own pack count.
 * The refunded value is the sum of the pack's <em>trailing</em> per-lesson slots
 * (defect #3: largest-remainder slots, not a flat {@code discounted / packCount} —
 * FIFO consumes the leading slots, so the unused value is the tail). A full-pack
 * refund therefore returns exactly {@code discounted}, and the credit is issued
 * idempotently on a key derived from the payment id; the payment is moved to
 * REFUNDED so it cannot be approved twice.</p>
 *
 * <p>This path is distinct from the provider real-refund
 * ({@code markRefundedByProvider}): a payment already REFUNDED (by either path)
 * is rejected here, which blocks double-refunds across the two channels.</p>
 */
@Service
public class PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final CreditService creditService;
    private final HaruCreditAccountRepository creditAccountRepository;
    private final PlatformSettingsService platformSettingsService;

    public PaymentRefundService(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            CreditService creditService,
            HaruCreditAccountRepository creditAccountRepository,
            PlatformSettingsService platformSettingsService
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.creditService = creditService;
        this.creditAccountRepository = creditAccountRepository;
        this.platformSettingsService = platformSettingsService;
    }

    /**
     * Admin refund queue (#11): all payments currently in REFUND_REQUESTED, oldest
     * first. Read-only; the entity graph (student / tutorProfile) is eagerly fetched
     * by the repository so the DTO mapping never lazy-loads outside the transaction.
     */
    @Transactional(readOnly = true)
    public List<RefundRequestResponse> listRefundRequests() {
        return paymentRepository.findAllByStatusOrderByCreatedAtAsc(PaymentStatus.REFUND_REQUESTED)
                .stream()
                .map(RefundRequestResponse::from)
                .toList();
    }

    @Transactional
    public PaymentResponse approveRefund(Long paymentId) {
        Payment payment = paymentRepository.findWithDetailsById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment was not found."));

        // Only a paid / refund-requested payment can be approved. A payment already
        // REFUNDED (real refund or credit refund) is blocked = no double refund.
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Payment was already refunded.");
        }
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.REFUND_REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only paid or refund-requested payments can be approved for refund.");
        }

        Long studentId = payment.getStudent().getId();
        Long tutorProfileId = payment.getTutorProfile().getId();
        int duration = payment.getLessonDurationMinutes();
        Instant now = Instant.now();

        // Defect #4 (refund concurrency): take a pessimistic write lock on the
        // student's single credit-account row BEFORE computing the unused total.
        // Concurrent refund approvals for the same student (e.g. two admins, or a
        // retry racing the original) serialize on this row, so the unused-lesson
        // calculation below always reads a committed, post-previous-refund state
        // instead of a stale snapshot — preventing over-refunding the same lessons.
        // The whole method is one @Transactional, so the lock is held until commit,
        // by which point this payment's PAID->REFUNDED transition is also committed.
        lockStudentCreditAccount(studentId, now);

        // The unused-lesson total is recomputed AFTER the lock with fresh queries
        // ({@code sumLessonPackCount(...PAID)} / {@code countConsumedLessons}). A
        // racing refund that committed first has already flipped its own payment out
        // of PAID, so it drops out of {@code totalPaidLessons} here — the second
        // refund sees the reduced budget and cannot re-refund the same lessons.
        long totalPaidLessons = paymentRepository.sumLessonPackCount(studentId, tutorProfileId, duration, PaymentStatus.PAID);
        long consumedLessons = bookingRepository.countConsumedLessons(studentId, tutorProfileId, duration);
        long unusedTotal = Math.max(0L, totalPaidLessons - consumedLessons);
        // Cap the refund to this payment's own pack — never refund more than purchased.
        int refundableUnits = (int) Math.min(unusedTotal, payment.getLessonPackCount());

        // Refund the pack's TRAILING slots (FIFO burns the leading ones). Using the
        // largest-remainder slot list keeps refund + earned grosses summing exactly
        // to the pack's discounted total — no per-lesson rounding leak (defect #3).
        java.util.List<BigDecimal> slots = payment.perLessonSlots();
        BigDecimal refundAmountUsd = BigDecimal.ZERO.setScale(2);
        for (int i = slots.size() - refundableUnits; i < slots.size(); i++) {
            refundAmountUsd = refundAmountUsd.add(slots.get(i));
        }

        if (refundAmountUsd.signum() > 0) {
            creditService.issueRefund(
                    studentId,
                    refundAmountUsd,
                    payment.getId(),
                    "REFUND_ISSUED:payment:" + payment.getId(),
                    "Refund of " + refundableUnits + " unused lesson(s) for payment #" + payment.getId(),
                    now
            );
        }

        payment.approveRefundAsCredit(refundAmountUsd, now);

        // Return the updated payment (status REFUNDED). The issued credit is on the
        // student's credit account; the credited USD is also on the payment as
        // refundCreditAmountUsd (see GET /api/credits/me for the ledger).
        return PaymentResponse.from(payment);
    }

    /**
     * Acquire (or create + acquire) the pessimistic write lock on the student's
     * single Haru credit-account row. If the account does not exist yet it is
     * created first and then re-loaded {@code FOR UPDATE}, so the lock is held even
     * for a student's first-ever refund. Returns the locked account.
     */
    private HaruCreditAccount lockStudentCreditAccount(Long studentId, Instant now) {
        return creditAccountRepository.findByUserIdForUpdate(studentId)
                .orElseGet(() -> {
                    int expiryMonths = platformSettingsService.currentFeePolicy().creditExpiryMonths();
                    creditAccountRepository.saveAndFlush(HaruCreditAccount.open(studentId, expiryMonths, now));
                    return creditAccountRepository.findByUserIdForUpdate(studentId)
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.INVALID_REQUEST,
                                    "Could not lock the student's credit account for refund."));
                });
    }
}
