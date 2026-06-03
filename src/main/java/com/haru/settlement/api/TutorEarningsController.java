package com.haru.settlement.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.settlement.api.dto.CreateWithdrawalRequest;
import com.haru.settlement.api.dto.SettlementListResponse;
import com.haru.settlement.api.dto.TutorEarningsResponse;
import com.haru.settlement.api.dto.WithdrawalListResponse;
import com.haru.settlement.api.dto.WithdrawalResponse;
import com.haru.settlement.application.SettlementService;
import com.haru.settlement.application.SettlementService.EarningTotals;
import com.haru.settlement.application.TutorOwnershipResolver;
import com.haru.settlement.application.WithdrawalService;
import com.haru.settlement.domain.Withdrawal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tutor-facing money endpoints. Path security requires authentication; the TUTOR
 * role and profile ownership are enforced in {@link TutorOwnershipResolver}.
 */
@RestController
@Tag(name = "TutorEarnings", description = "튜터 사이버머니 잔액/원장, 인출, 월정산 조회 API")
public class TutorEarningsController {

    private final SettlementService settlementService;
    private final WithdrawalService withdrawalService;
    private final TutorOwnershipResolver tutorOwnershipResolver;

    public TutorEarningsController(
            SettlementService settlementService,
            WithdrawalService withdrawalService,
            TutorOwnershipResolver tutorOwnershipResolver
    ) {
        this.settlementService = settlementService;
        this.withdrawalService = withdrawalService;
        this.tutorOwnershipResolver = tutorOwnershipResolver;
    }

    @Operation(summary = "내 사이버머니 잔액/원장 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/tutors/me/earnings")
    public ApiResponse<TutorEarningsResponse> getMyEarnings(@AuthenticationPrincipal HaruPrincipal principal) {
        Long tutorProfileId = tutorOwnershipResolver.requireOwnTutorProfileId(principal);
        EarningTotals totals = settlementService.earningTotals(tutorProfileId);
        return ApiResponse.success(TutorEarningsResponse.of(
                tutorProfileId,
                totals.availableBalance(),
                totals.totalEarned(),
                totals.totalWithdrawn(),
                totals.pendingWithdrawal(),
                settlementService.ledgerFor(tutorProfileId)
        ));
    }

    @Operation(summary = "인출 요청", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/tutors/me/withdrawals")
    public ApiResponse<WithdrawalResponse> requestWithdrawal(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody CreateWithdrawalRequest request
    ) {
        Long tutorProfileId = tutorOwnershipResolver.requireOwnTutorProfileId(principal);
        Withdrawal withdrawal = withdrawalService.request(
                tutorProfileId, request.method(), request.amount(), request.payoutAccount());
        return ApiResponse.success(WithdrawalResponse.from(withdrawal));
    }

    @Operation(summary = "내 인출 내역 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/tutors/me/withdrawals")
    public ApiResponse<WithdrawalListResponse> getMyWithdrawals(@AuthenticationPrincipal HaruPrincipal principal) {
        Long tutorProfileId = tutorOwnershipResolver.requireOwnTutorProfileId(principal);
        return ApiResponse.success(WithdrawalListResponse.from(withdrawalService.getForTutor(tutorProfileId)));
    }

    @Operation(summary = "내 월정산 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/tutors/me/settlements")
    public ApiResponse<SettlementListResponse> getMySettlements(@AuthenticationPrincipal HaruPrincipal principal) {
        Long tutorProfileId = tutorOwnershipResolver.requireOwnTutorProfileId(principal);
        return ApiResponse.success(SettlementListResponse.from(settlementService.settlementsFor(tutorProfileId)));
    }
}
