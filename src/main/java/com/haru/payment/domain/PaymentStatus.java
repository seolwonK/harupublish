package com.haru.payment.domain;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED,
    REFUND_REQUESTED,
    REFUNDED
}
