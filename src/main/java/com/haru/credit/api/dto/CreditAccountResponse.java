package com.haru.credit.api.dto;

import com.haru.credit.domain.HaruCreditAccount;
import com.haru.credit.domain.HaruCreditLedger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Haru credit account view. Field names follow the frontend money contract:
 * {@code studentUserId}, {@code balanceAmount}, {@code ledger}.
 */
public record CreditAccountResponse(
        Long accountId,
        Long studentUserId,
        String currency,
        BigDecimal balanceAmount,
        Instant expiresAt,
        List<CreditLedgerEntryResponse> ledger
) {

    /** Empty account view for a user who has never received credit. */
    public static CreditAccountResponse empty(Long userId) {
        return new CreditAccountResponse(
                null,
                userId,
                HaruCreditAccount.CURRENCY,
                BigDecimal.ZERO.setScale(2),
                null,
                List.of()
        );
    }

    public static CreditAccountResponse from(HaruCreditAccount account, List<HaruCreditLedger> ledger) {
        return new CreditAccountResponse(
                account.getId(),
                account.getUserId(),
                account.getCurrency(),
                account.getBalanceUsd(),
                account.getExpiresAt(),
                ledger.stream().map(CreditLedgerEntryResponse::from).toList()
        );
    }
}
