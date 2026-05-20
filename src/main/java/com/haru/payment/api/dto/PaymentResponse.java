package com.haru.payment.api.dto;

import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentMethod;
import com.haru.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long studentUserId,
        Long tutorProfileId,
        Integer lessonDurationMinutes,
        Integer lessonPackCount,
        BigDecimal unitAmount,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal studentFeeAmount,
        BigDecimal totalAmount,
        String currency,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String refundReason,
        String provider,
        String providerCheckoutId,
        String checkoutUrl,
        String providerOrderId,
        String providerOrderIdentifier,
        Instant createdAt,
        Instant updatedAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStudent().getId(),
                payment.getTutorProfile().getId(),
                payment.getLessonDurationMinutes(),
                payment.getLessonPackCount(),
                payment.getUnitAmount(),
                payment.getSubtotalAmount(),
                payment.getDiscountAmount(),
                payment.getStudentFeeAmount(),
                payment.getTotalAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getRefundReason(),
                payment.getProvider(),
                payment.getProviderCheckoutId(),
                payment.getProviderCheckoutUrl(),
                payment.getProviderOrderId(),
                payment.getProviderOrderIdentifier(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
