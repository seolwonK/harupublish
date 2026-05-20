package com.haru.payment.api.dto;

import com.haru.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CreateCheckoutRequest(
        @NotNull
        Long tutorProfileId,

        @NotNull
        Integer lessonDurationMinutes,

        @NotNull
        Integer lessonPackCount,

        @NotNull
        PaymentMethod paymentMethod
) {
}
