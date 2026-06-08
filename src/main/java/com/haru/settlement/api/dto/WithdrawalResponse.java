package com.haru.settlement.api.dto;

import com.haru.settlement.domain.Withdrawal;
import com.haru.settlement.domain.WithdrawalMethod;
import com.haru.settlement.domain.WithdrawalStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Withdrawal view. Field names follow the frontend money contract: amounts are
 * USD-denominated ({@code currency} = "USD"), {@code payoutAccount} mirrors the
 * stored payout reference.
 */
public record WithdrawalResponse(
        Long id,
        Long tutorProfileId,
        WithdrawalMethod method,
        BigDecimal requestedAmount,
        BigDecimal feeRate,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        String currency,
        WithdrawalStatus status,
        boolean promoFeeWaived,
        boolean promoPaybackPending,
        BigDecimal promoPaybackAmount,
        String payoutAccount,
        String rejectReason,
        Instant requestedAt,
        Instant approvedAt,
        Instant paidAt,
        Instant rejectedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static WithdrawalResponse from(Withdrawal withdrawal) {
        return new WithdrawalResponse(
                withdrawal.getId(),
                withdrawal.getTutorProfileId(),
                withdrawal.getMethod(),
                withdrawal.getRequestedAmountUsd(),
                withdrawal.getFeeRate(),
                withdrawal.getFeeAmountUsd(),
                withdrawal.getNetPayoutUsd(),
                "USD",
                withdrawal.getStatus(),
                withdrawal.isPromoFeeWaived(),
                withdrawal.isPromoPaybackPending(),
                withdrawal.getPromoPaybackAmountUsd(),
                withdrawal.getPayoutReference(),
                withdrawal.getRejectReason(),
                withdrawal.getRequestedAt(),
                withdrawal.getApprovedAt(),
                withdrawal.getPaidAt(),
                withdrawal.getRejectedAt(),
                withdrawal.getCreatedAt(),
                withdrawal.getUpdatedAt()
        );
    }
}
