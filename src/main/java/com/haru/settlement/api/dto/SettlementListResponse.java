package com.haru.settlement.api.dto;

import com.haru.settlement.domain.MonthlySettlement;

import java.util.List;

public record SettlementListResponse(
        List<MonthlySettlementResponse> settlements
) {

    public static SettlementListResponse from(List<MonthlySettlement> settlements) {
        return new SettlementListResponse(settlements.stream().map(MonthlySettlementResponse::from).toList());
    }
}
