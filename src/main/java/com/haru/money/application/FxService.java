package com.haru.money.application;

import com.haru.money.domain.ExchangeRate;
import com.haru.money.infra.ExchangeRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Display-only currency conversion. The truth ledger stays in USD; this service
 * resolves the latest cached {@link ExchangeRate} so callers can show a derived
 * KRW (or other quote) value next to a USD amount. If no rate is cached the
 * conversion safely returns empty — the USD ledger is never affected.
 */
@Service
public class FxService {

    public static final String BASE_CURRENCY = "USD";
    public static final String DEFAULT_QUOTE_CURRENCY = "KRW";

    private final ExchangeRateRepository exchangeRateRepository;

    public FxService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * Latest cached rate for the given pair, if any. Never throws when missing —
     * callers fall back to USD-only display.
     */
    @Transactional(readOnly = true)
    public Optional<ExchangeRate> latestRate(String baseCurrency, String quoteCurrency) {
        return exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByCapturedAtDesc(
                normalize(baseCurrency, BASE_CURRENCY),
                normalize(quoteCurrency, DEFAULT_QUOTE_CURRENCY)
        );
    }

    /**
     * Convert a USD amount to the quote currency for display. Returns empty when
     * no rate is cached so the caller can show the USD figure alone. The result
     * is rounded HALF_UP to 2 decimals (presentation only).
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> convertForDisplay(BigDecimal usdAmount, String quoteCurrency) {
        if (usdAmount == null) {
            return Optional.empty();
        }
        return latestRate(BASE_CURRENCY, quoteCurrency)
                .map(rate -> usdAmount.multiply(rate.getRate()).setScale(2, RoundingMode.HALF_UP));
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase();
    }
}
