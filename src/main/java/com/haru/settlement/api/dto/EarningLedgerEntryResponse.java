package com.haru.settlement.api.dto;

import com.haru.settlement.domain.EarningEntryType;
import com.haru.settlement.domain.TutorEarningLedger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One tutor earning ledger entry. Field names follow the frontend money
 * contract: {@code amount} is the signed movement, {@code balanceAfter} the
 * running balance.
 */
public record EarningLedgerEntryResponse(
        Long id,
        Long bookingId,
        Long withdrawalId,
        EarningEntryType entryType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String currency,
        String memo,
        Instant createdAt
) {

    public static EarningLedgerEntryResponse from(TutorEarningLedger entry) {
        return new EarningLedgerEntryResponse(
                entry.getId(),
                entry.getBookingId(),
                entry.getWithdrawalId(),
                entry.getEntryType(),
                entry.getNetAmountUsd(),
                entry.getBalanceAfterUsd(),
                "USD",
                entry.getMemo(),
                entry.getCreatedAt()
        );
    }
}
