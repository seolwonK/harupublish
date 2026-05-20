package com.haru.payment.api.dto;

import com.haru.payment.domain.Payment;

import java.util.List;

public record PaymentListResponse(
        List<PaymentResponse> payments
) {

    public static PaymentListResponse from(List<Payment> payments) {
        return new PaymentListResponse(
                payments.stream()
                        .map(PaymentResponse::from)
                        .toList()
        );
    }
}
