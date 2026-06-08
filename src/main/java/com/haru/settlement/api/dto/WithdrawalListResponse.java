package com.haru.settlement.api.dto;

import com.haru.settlement.domain.Withdrawal;

import java.util.List;

public record WithdrawalListResponse(
        List<WithdrawalResponse> withdrawals
) {

    public static WithdrawalListResponse from(List<Withdrawal> withdrawals) {
        return new WithdrawalListResponse(withdrawals.stream().map(WithdrawalResponse::from).toList());
    }
}
