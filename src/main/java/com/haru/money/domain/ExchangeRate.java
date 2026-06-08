package com.haru.money.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cached USD-base exchange rate used only for display conversion. The truth
 * ledger is always USD; KRW (or any quote currency) is a derived presentation
 * value, so this row never affects settlement, earnings, or credits.
 */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(name = "rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "valid_until")
    private Instant validUntil;

    protected ExchangeRate() {
    }

    private ExchangeRate(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            String source,
            Instant capturedAt,
            Instant validUntil
    ) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rate = rate;
        this.source = source;
        this.capturedAt = capturedAt;
        this.validUntil = validUntil;
    }

    public static ExchangeRate capture(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            String source,
            Instant capturedAt,
            Instant validUntil
    ) {
        return new ExchangeRate(baseCurrency, quoteCurrency, rate, source, capturedAt, validUntil);
    }

    public Long getId() {
        return id;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getSource() {
        return source;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getValidUntil() {
        return validUntil;
    }
}
