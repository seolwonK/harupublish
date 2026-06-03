package com.haru.settlement.api.dto;

import com.haru.settlement.domain.EarningEntryType;
import com.haru.settlement.domain.TutorEarningLedger;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tutor cyber-money summary + ledger. Field names follow the frontend money
 * contract:
 * <ul>
 *   <li>{@code totalEarnedAmount} = sum of positive earning/adjustment entries
 *       (lesson, no-show, payback) — never reduced by withdrawals.</li>
 *   <li>{@code totalWithdrawnAmount} = sum of holds (debits) that have not been
 *       reversed, i.e. amounts that left or are leaving the balance.</li>
 *   <li>{@code pendingWithdrawalAmount} = holds not yet reversed nor paid.</li>
 *   <li>{@code availableBalanceAmount} = current running balance.</li>
 * </ul>
 */
public record TutorEarningsResponse(
        Long tutorProfileId,
        String currency,
        BigDecimal totalEarnedAmount,
        BigDecimal totalWithdrawnAmount,
        BigDecimal availableBalanceAmount,
        BigDecimal pendingWithdrawalAmount,
        List<EarningLedgerEntryResponse> ledger
) {

    public static TutorEarningsResponse of(
            Long tutorProfileId,
            BigDecimal balanceUsd,
            BigDecimal totalEarnedUsd,
            BigDecimal totalWithdrawnUsd,
            BigDecimal pendingWithdrawalUsd,
            List<TutorEarningLedger> ledger
    ) {
        return new TutorEarningsResponse(
                tutorProfileId,
                "USD",
                totalEarnedUsd,
                totalWithdrawnUsd,
                balanceUsd,
                pendingWithdrawalUsd,
                ledger.stream().map(EarningLedgerEntryResponse::from).toList()
        );
    }

    /** Returns true for entry types that count toward {@code totalEarnedAmount}. */
    public static boolean isEarning(EarningEntryType type) {
        return type == EarningEntryType.LESSON_EARNED
                || type == EarningEntryType.NO_SHOW_EARNED
                || type == EarningEntryType.ADJUSTMENT;
    }
}
