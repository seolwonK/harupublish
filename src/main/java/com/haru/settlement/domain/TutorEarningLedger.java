package com.haru.settlement.domain;

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
 * Append-only ledger of a tutor's USD "cyber money". Each row records a single
 * earning (or future adjustment) and the running {@code balanceAfterUsd} so the
 * latest row holds the current balance. The platform fee (default 15%) is taken
 * out before the net is credited, per money model 1.
 */
@Entity
@Table(name = "tutor_earning_ledger")
public class TutorEarningLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tutor_profile_id", nullable = false)
    private Long tutorProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private EarningEntryType entryType;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "gross_amount_usd", precision = 12, scale = 2)
    private BigDecimal grossAmountUsd;

    @Column(name = "platform_fee_amount_usd", precision = 12, scale = 2)
    private BigDecimal platformFeeAmountUsd;

    @Column(name = "net_amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmountUsd;

    @Column(name = "balance_after_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfterUsd;

    @Column(name = "applied_platform_fee_rate", precision = 6, scale = 4)
    private BigDecimal appliedPlatformFeeRate;

    @Column(name = "withdrawal_id")
    private Long withdrawalId;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TutorEarningLedger() {
    }

    private TutorEarningLedger(
            Long tutorProfileId,
            EarningEntryType entryType,
            Long bookingId,
            BigDecimal grossAmountUsd,
            BigDecimal platformFeeAmountUsd,
            BigDecimal netAmountUsd,
            BigDecimal balanceAfterUsd,
            BigDecimal appliedPlatformFeeRate,
            Long withdrawalId,
            String idempotencyKey,
            String memo,
            Instant createdAt
    ) {
        this.tutorProfileId = tutorProfileId;
        this.entryType = entryType;
        this.bookingId = bookingId;
        this.grossAmountUsd = grossAmountUsd;
        this.platformFeeAmountUsd = platformFeeAmountUsd;
        this.netAmountUsd = netAmountUsd;
        this.balanceAfterUsd = balanceAfterUsd;
        this.appliedPlatformFeeRate = appliedPlatformFeeRate;
        this.withdrawalId = withdrawalId;
        this.idempotencyKey = idempotencyKey;
        this.memo = memo;
        this.createdAt = createdAt;
    }

    public static TutorEarningLedger lessonEarning(
            Long tutorProfileId,
            EarningEntryType entryType,
            Long bookingId,
            BigDecimal grossAmountUsd,
            BigDecimal platformFeeAmountUsd,
            BigDecimal netAmountUsd,
            BigDecimal previousBalanceUsd,
            BigDecimal appliedPlatformFeeRate,
            String memo,
            Instant now
    ) {
        return lessonEarning(
                tutorProfileId,
                entryType,
                bookingId,
                grossAmountUsd,
                platformFeeAmountUsd,
                netAmountUsd,
                previousBalanceUsd,
                appliedPlatformFeeRate,
                null,
                memo,
                now
        );
    }

    /**
     * Lesson earning with an explicit {@code idempotencyKey}. The key
     * ({@code EARNED:booking:{id}:{entryType}}) is a secondary backstop against a
     * double credit, complementing the unique(booking_id, entry_type) constraint.
     */
    public static TutorEarningLedger lessonEarning(
            Long tutorProfileId,
            EarningEntryType entryType,
            Long bookingId,
            BigDecimal grossAmountUsd,
            BigDecimal platformFeeAmountUsd,
            BigDecimal netAmountUsd,
            BigDecimal previousBalanceUsd,
            BigDecimal appliedPlatformFeeRate,
            String idempotencyKey,
            String memo,
            Instant now
    ) {
        BigDecimal balanceAfter = previousBalanceUsd.add(netAmountUsd);
        return new TutorEarningLedger(
                tutorProfileId,
                entryType,
                bookingId,
                grossAmountUsd,
                platformFeeAmountUsd,
                netAmountUsd,
                balanceAfter,
                appliedPlatformFeeRate,
                null,
                idempotencyKey,
                memo,
                now
        );
    }

    /**
     * A non-lesson balance movement (withdrawal hold/reverse, promo payback, or a
     * manual adjustment). {@code netAmountUsd} is signed: negative for a HOLD,
     * positive for a REVERSE/PAYBACK. The running balance is recomputed from the
     * supplied previous balance. {@code idempotencyKey} de-duplicates the entry.
     */
    public static TutorEarningLedger adjustment(
            Long tutorProfileId,
            EarningEntryType entryType,
            BigDecimal netAmountUsd,
            BigDecimal previousBalanceUsd,
            Long withdrawalId,
            String idempotencyKey,
            String memo,
            Instant now
    ) {
        BigDecimal balanceAfter = previousBalanceUsd.add(netAmountUsd);
        return new TutorEarningLedger(
                tutorProfileId,
                entryType,
                null,
                null,
                null,
                netAmountUsd,
                balanceAfter,
                null,
                withdrawalId,
                idempotencyKey,
                memo,
                now
        );
    }

    public Long getId() {
        return id;
    }

    public Long getTutorProfileId() {
        return tutorProfileId;
    }

    public EarningEntryType getEntryType() {
        return entryType;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public BigDecimal getGrossAmountUsd() {
        return grossAmountUsd;
    }

    public BigDecimal getPlatformFeeAmountUsd() {
        return platformFeeAmountUsd;
    }

    public BigDecimal getNetAmountUsd() {
        return netAmountUsd;
    }

    public BigDecimal getBalanceAfterUsd() {
        return balanceAfterUsd;
    }

    public BigDecimal getAppliedPlatformFeeRate() {
        return appliedPlatformFeeRate;
    }

    public Long getWithdrawalId() {
        return withdrawalId;
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
