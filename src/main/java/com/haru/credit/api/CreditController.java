package com.haru.credit.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.credit.api.dto.CreditAccountResponse;
import com.haru.credit.application.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credits")
@Tag(name = "Credit", description = "Haru 크레딧(환불 전용 가상화폐, USD) 조회 API")
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    @Operation(summary = "내 Haru 크레딧 잔액/원장 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ApiResponse<CreditAccountResponse> getMyCredits(@AuthenticationPrincipal HaruPrincipal principal) {
        Long userId = principal.userId();
        return ApiResponse.success(creditService.findAccount(userId)
                .map(account -> CreditAccountResponse.from(account, creditService.ledgerFor(account.getId())))
                .orElseGet(() -> CreditAccountResponse.empty(userId)));
    }
}
