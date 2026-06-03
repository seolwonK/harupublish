package com.haru.credit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only ledger of Haru credit movements. Each row records a signed
 * {@code amountUsd} (positive for REFUND_ISSUED, negative for SPENT/EXPIRED) and
 * the running {@code balanceAfterUsd}. An {@code idempotencyKey} de-duplicates
 * refund issuances so a refund cannot be double-credited.
 */
@Entity
@Table(name = "haru_credit_ledger")
public class HaruCreditLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private HaruCreditEntryType entryType;

    @Column(name = "amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountUsd;

    @Column(name = "balance_after_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfterUsd;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HaruCreditLedger() {
    }

    private HaruCreditLedger(
            Long accountId,
            HaruCreditEntryType entryType,
            BigDecimal amountUsd,
            BigDecimal balanceAfterUsd,
            Long paymentId,
            String idempotencyKey,
            String memo,
            Instant createdAt
    ) {
        this.accountId = accountId;
        this.entryType = entryType;
        this.amountUsd = amountUsd;
        this.balanceAfterUsd = balanceAfterUsd;
        this.paymentId = paymentId;
        this.idempotencyKey = idempotencyKey;
        this.memo = memo;
        this.createdAt = createdAt;
    }

    public static HaruCreditLedger refundIssued(
            Long accountId,
            BigDecimal amountUsd,
            BigDecimal balanceAfterUsd,
            Long paymentId,
            String idempotencyKey,
            String memo,
            Instant now
    ) {
        return new HaruCreditLedger(accountId, HaruCreditEntryType.REFUND_ISSUED, amountUsd, balanceAfterUsd, paymentId, idempotencyKey, memo, now);
    }

    public static HaruCreditLedger spent(
            Long accountId,
            BigDecimal amountUsd,
            BigDecimal balanceAfterUsd,
            Long paymentId,
            String idempotencyKey,
            String memo,
            Instant now
    ) {
        return new HaruCreditLedger(accountId, HaruCreditEntryType.SPENT, amountUsd.negate(), balanceAfterUsd, paymentId, idempotencyKey, memo, now);
    }

    public static HaruCreditLedger expired(
            Long accountId,
            BigDecimal amountUsd,
            BigDecimal balanceAfterUsd,
            String memo,
            Instant now
    ) {
        return new HaruCreditLedger(accountId, HaruCreditEntryType.EXPIRED, amountUsd.negate(), balanceAfterUsd, null, null, memo, now);
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public HaruCreditEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmountUsd() {
        return amountUsd;
    }

    public BigDecimal getBalanceAfterUsd() {
        return balanceAfterUsd;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getMemo() {
        return memo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
