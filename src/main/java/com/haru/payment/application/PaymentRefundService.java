package com.haru.payment.application;

import com.haru.booking.infra.BookingRepository;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.NotFoundException;
import com.haru.credit.application.CreditService;
import com.haru.payment.api.dto.PaymentResponse;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentStatus;
import com.haru.payment.infra.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin refund approval -> Haru credit issuance (E2E).
 *
 * <p>A refund only ever returns <b>unused</b> lesson value as Haru credit (no
 * cash-out). The refundable units for a payment are the still-unused lessons for
 * its (student, tutor, 25-minute) tuple, capped at the payment's own pack count.
 * Each unused unit is worth the payment's per-lesson tutor gross
 * ({@code discounted / packCount}). The credit is issued idempotently on a key
 * derived from the payment id, and the payment is moved to REFUNDED so it cannot
 * be approved twice.</p>
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

    public PaymentRefundService(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            CreditService creditService
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.creditService = creditService;
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

        long totalPaidLessons = paymentRepository.sumLessonPackCount(studentId, tutorProfileId, duration, PaymentStatus.PAID);
        long consumedLessons = bookingRepository.countConsumedLessons(studentId, tutorProfileId, duration);
        long unusedTotal = Math.max(0L, totalPaidLessons - consumedLessons);
        // Cap the refund to this payment's own pack — never refund more than purchased.
        long refundableUnits = Math.min(unusedTotal, payment.getLessonPackCount());

        BigDecimal perUnitUsd = payment.perLessonDiscountedAmount();
        BigDecimal refundAmountUsd = perUnitUsd.multiply(BigDecimal.valueOf(refundableUnits));

        Instant now = Instant.now();
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
}
