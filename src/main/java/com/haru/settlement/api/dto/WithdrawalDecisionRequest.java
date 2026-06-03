package com.haru.settlement.api.dto;

/**
 * Optional admin notes when transitioning a withdrawal: {@code payoutReference}
 * is used on "paid", {@code reason} on "reject". Both are optional.
 */
public record WithdrawalDecisionRequest(
        String payoutReference,
        String reason
) {
}
