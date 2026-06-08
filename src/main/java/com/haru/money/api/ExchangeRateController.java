package com.haru.money.api;

import com.haru.common.exception.NotFoundException;
import com.haru.common.response.ApiResponse;
import com.haru.money.api.dto.ExchangeRateResponse;
import com.haru.money.application.FxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exchange-rates")
@Tag(name = "ExchangeRate", description = "표시용 환율 조회 API (진실원장은 USD, KRW 등은 표시 파생)")
public class ExchangeRateController {

    private final FxService fxService;

    public ExchangeRateController(FxService fxService) {
        this.fxService = fxService;
    }

    @Operation(summary = "최신 환율 조회", description = "표시용 최신 USD 기준 환율을 조회합니다. 기본값 base=USD, quote=KRW.")
    @GetMapping("/latest")
    public ApiResponse<ExchangeRateResponse> latest(
            @RequestParam(name = "base", defaultValue = FxService.BASE_CURRENCY) String base,
            @RequestParam(name = "quote", defaultValue = FxService.DEFAULT_QUOTE_CURRENCY) String quote
    ) {
        ExchangeRateResponse response = fxService.latestRate(base, quote)
                .map(ExchangeRateResponse::from)
                .orElseThrow(() -> new NotFoundException("No exchange rate is available for the requested pair."));
        return ApiResponse.success(response);
    }
}
