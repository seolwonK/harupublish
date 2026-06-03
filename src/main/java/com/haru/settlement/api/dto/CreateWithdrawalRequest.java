package com.haru.settlement.api.dto;

import com.haru.settlement.domain.WithdrawalMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Withdrawal request body. Field names follow the frontend money contract:
 * {@code amount} (USD) and {@code payoutAccount} (the tutor's payout target).
 */
public record CreateWithdrawalRequest(
        @NotNull
        WithdrawalMethod method,
        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,
        String payoutAccount
) {
}
