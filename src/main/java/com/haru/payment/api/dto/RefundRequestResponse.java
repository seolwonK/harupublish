package com.haru.payment.api.dto;

import com.haru.payment.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin refund-queue row (#11): one REFUND_REQUESTED payment awaiting approval.
 * Returned by {@code GET /api/admin/payments/refund-requests}. Display-only — the
 * approval itself goes through {@code POST /api/admin/payments/{id}/refund-approve}.
 */
public record RefundRequestResponse(
        Long id,
        Long studentUserId,
        Long tutorProfileId,
        BigDecimal totalAmount,
        String currency,
        String refundReason,
        Instant createdAt
) {

    public static RefundRequestResponse from(Payment payment) {
        return new RefundRequestResponse(
                payment.getId(),
                payment.getStudent().getId(),
                payment.getTutorProfile().getId(),
                payment.getTotalAmount(),
                payment.getCurrency(),
                payment.getRefundReason(),
                payment.getCreatedAt()
        );
    }
}
