package com.haru.money.api.dto;

import com.haru.money.domain.ExchangeRate;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRateResponse(
        String base,
        String quote,
        BigDecimal rate,
        String source,
        Instant capturedAt
) {

    public static ExchangeRateResponse from(ExchangeRate exchangeRate) {
        return new ExchangeRateResponse(
                exchangeRate.getBaseCurrency(),
                exchangeRate.getQuoteCurrency(),
                exchangeRate.getRate(),
                exchangeRate.getSource(),
                exchangeRate.getCapturedAt()
        );
    }
}
