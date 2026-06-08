package com.haru.settlement.api.dto;

import com.haru.settlement.domain.MonthlySettlement;
import com.haru.settlement.domain.MonthlySettlementStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Monthly settlement view. Field names follow the frontend money contract:
 * {@code year}/{@code month} and USD amounts with a {@code currency} field.
 */
public record MonthlySettlementResponse(
        Long id,
        Long tutorProfileId,
        int year,
        int month,
        BigDecimal grossAmount,
        BigDecimal platformFeeAmount,
        BigDecimal netAmount,
        int lessonCount,
        String currency,
        MonthlySettlementStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static MonthlySettlementResponse from(MonthlySettlement settlement) {
        return new MonthlySettlementResponse(
                settlement.getId(),
                settlement.getTutorProfileId(),
                settlement.getSettlementYear(),
                settlement.getSettlementMonth(),
                settlement.getGrossAmountUsd(),
                settlement.getPlatformFeeAmountUsd(),
                settlement.getNetAmountUsd(),
                settlement.getLessonCount(),
                "USD",
                settlement.getStatus(),
                settlement.getCreatedAt(),
                settlement.getUpdatedAt()
        );
    }
}
