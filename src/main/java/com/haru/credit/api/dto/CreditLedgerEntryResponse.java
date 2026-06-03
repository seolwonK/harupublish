package com.haru.credit.api.dto;

import com.haru.credit.domain.HaruCreditEntryType;
import com.haru.credit.domain.HaruCreditLedger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One Haru credit ledger entry. Field names follow the frontend money contract.
 */
public record CreditLedgerEntryResponse(
        Long id,
        HaruCreditEntryType entryType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String currency,
        Long paymentId,
        String memo,
        Instant createdAt
) {

    public static CreditLedgerEntryResponse from(HaruCreditLedger entry) {
        return new CreditLedgerEntryResponse(
                entry.getId(),
                entry.getEntryType(),
                entry.getAmountUsd(),
                entry.getBalanceAfterUsd(),
                "USD",
                entry.getPaymentId(),
                entry.getMemo(),
                entry.getCreatedAt()
        );
    }
}
