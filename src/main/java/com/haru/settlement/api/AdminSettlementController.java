package com.haru.settlement.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.settlement.api.dto.MonthlySettlementResponse;
import com.haru.settlement.api.dto.PromoFeeWaiverResponse;
import com.haru.settlement.api.dto.UpdateSettlementStatusRequest;
import com.haru.settlement.api.dto.WithdrawalDecisionRequest;
import com.haru.settlement.api.dto.WithdrawalListResponse;
import com.haru.settlement.api.dto.WithdrawalResponse;
import com.haru.settlement.application.PromoFeeWaiverService;
import com.haru.settlement.application.SettlementService;
import com.haru.settlement.application.WithdrawalService;
import com.haru.settlement.domain.WithdrawalStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin money operations: withdrawal review, monthly settlement status, and the
 * promo platform-fee waiver. Path security ({@code /api/admin/**}) already
 * requires ADMIN.
 */
@RestController
@Tag(name = "AdminSettlement", description = "관리자 인출 승인/지급/반려, 월정산 상태, 프로모 면제 API")
public class AdminSettlementController {

    private final WithdrawalService withdrawalService;
    private final SettlementService settlementService;
    private final PromoFeeWaiverService promoFeeWaiverService;

    public AdminSettlementController(
            WithdrawalService withdrawalService,
            SettlementService settlementService,
            PromoFeeWaiverService promoFeeWaiverService
    ) {
        this.withdrawalService = withdrawalService;
        this.settlementService = settlementService;
        this.promoFeeWaiverService = promoFeeWaiverService;
    }

    @Operation(summary = "인출 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/admin/withdrawals")
    public ApiResponse<WithdrawalListResponse> listWithdrawals(
            @RequestParam(name = "status", required = false) WithdrawalStatus status
    ) {
        return ApiResponse.success(WithdrawalListResponse.from(withdrawalService.getAll(status)));
    }

    @Operation(summary = "인출 승인", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/admin/withdrawals/{withdrawalId}/approve")
    public ApiResponse<WithdrawalResponse> approveWithdrawal(@PathVariable Long withdrawalId) {
        return ApiResponse.success(WithdrawalResponse.from(withdrawalService.approve(withdrawalId)));
    }

    @Operation(summary = "인출 지급 완료", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/admin/withdrawals/{withdrawalId}/paid")
    public ApiResponse<WithdrawalResponse> markWithdrawalPaid(
            @PathVariable Long withdrawalId,
            @RequestBody(required = false) WithdrawalDecisionRequest request
    ) {
        String payoutReference = request == null ? null : request.payoutReference();
        return ApiResponse.success(WithdrawalResponse.from(withdrawalService.markPaid(withdrawalId, payoutReference)));
    }

    @Operation(summary = "인출 반려", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/admin/withdrawals/{withdrawalId}/reject")
    public ApiResponse<WithdrawalResponse> rejectWithdrawal(
            @PathVariable Long withdrawalId,
            @RequestBody(required = false) WithdrawalDecisionRequest request
    ) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success(WithdrawalResponse.from(withdrawalService.reject(withdrawalId, reason)));
    }

    @Operation(summary = "월정산 상태 변경", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/admin/settlements/{settlementId}/status")
    public ApiResponse<MonthlySettlementResponse> updateSettlementStatus(
            @PathVariable Long settlementId,
            @Valid @RequestBody UpdateSettlementStatusRequest request
    ) {
        return ApiResponse.success(MonthlySettlementResponse.from(
                settlementService.updateSettlementStatus(settlementId, request.status())
        ));
    }

    @Operation(summary = "프로모 수수료 면제 부여", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/admin/tutors/{tutorProfileId}/promo-waiver")
    public ApiResponse<PromoFeeWaiverResponse> grantPromoWaiver(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long tutorProfileId
    ) {
        Long adminId = principal == null ? null : principal.userId();
        return ApiResponse.success(PromoFeeWaiverResponse.from(
                promoFeeWaiverService.grant(tutorProfileId, adminId)
        ));
    }

    @Operation(summary = "프로모 수수료 면제 해제", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/api/admin/tutors/{tutorProfileId}/promo-waiver")
    public ApiResponse<Void> revokePromoWaiver(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long tutorProfileId
    ) {
        Long adminId = principal == null ? null : principal.userId();
        promoFeeWaiverService.revoke(tutorProfileId, adminId);
        return ApiResponse.success(null);
    }
}
