package com.haru.settings.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.settings.api.dto.FeeSettingsResponse;
import com.haru.settings.api.dto.UpdateFeeSettingsRequest;
import com.haru.settings.application.PlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings/fees")
@Tag(name = "AdminSettings", description = "관리자 런타임 수수료/정책 설정 API (append-only 버전 관리)")
public class AdminFeeSettingsController {

    private final PlatformSettingsService platformSettingsService;

    public AdminFeeSettingsController(PlatformSettingsService platformSettingsService) {
        this.platformSettingsService = platformSettingsService;
    }

    @Operation(summary = "현재 수수료 설정 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ApiResponse<FeeSettingsResponse> getFees() {
        return ApiResponse.success(FeeSettingsResponse.from(platformSettingsService.currentSettings()));
    }

    @Operation(
            summary = "수수료 설정 변경",
            description = "전달된 필드만 새 활성 버전으로 반영합니다(누락 필드는 현재값 유지). 과거 결제는 자체 스냅샷을 유지하므로 소급 적용되지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PutMapping
    public ApiResponse<FeeSettingsResponse> updateFees(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody UpdateFeeSettingsRequest request
    ) {
        return ApiResponse.success(FeeSettingsResponse.from(platformSettingsService.updateFees(
                request.studentFeeRate(),
                request.platformFeeRate(),
                request.resolvedPaypalRate(),
                request.withdrawalFeeRateDomestic(),
                request.fivePackDiscountRate(),
                request.tenPackDiscountRate(),
                request.promoWithdrawalFeeRate(),
                request.cancellationCutoffHours(),
                request.creditExpiryMonths(),
                principal == null ? null : principal.userId()
        )));
    }
}
