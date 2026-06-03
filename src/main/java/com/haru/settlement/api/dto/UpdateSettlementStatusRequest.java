package com.haru.settlement.api.dto;

import com.haru.settlement.domain.MonthlySettlementStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSettlementStatusRequest(
        @NotNull
        MonthlySettlementStatus status
) {
}
