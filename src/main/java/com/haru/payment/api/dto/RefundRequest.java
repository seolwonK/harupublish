package com.haru.payment.api.dto;

import jakarta.validation.constraints.Size;

public record RefundRequest(
        @Size(max = 500)
        String reason
) {
}
