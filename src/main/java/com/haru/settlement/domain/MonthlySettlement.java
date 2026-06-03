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
 * Per-(tutor, year, month) settlement rollup. Aggregates the tutor's earned
 * lessons for a calendar month into gross/fee/net totals for reporting and
 * payout. Independent of withdrawals; unique on (tutor, year, month).
 */
@Entity
@Table(name = "monthly_settlements")
public class MonthlySettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tutor_profile_id", nullable = false)
    private Long tutorProfileId;

    @Column(name = "settlement_year", nullable = false)
    private int settlementYear;

    @Column(name = "settlement_month", nullable = false)
    private int settlementMonth;

    @Column(name = "gross_amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmountUsd;

    @Column(name = "platform_fee_amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFeeAmountUsd;

    @Column(name = "net_amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmountUsd;

    @Column(name = "lesson_count", nullable = false)
    private int lessonCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MonthlySettlementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MonthlySettlement() {
    }

    private MonthlySettlement(Long tutorProfileId, int settlementYear, int settlementMonth, Instant now) {
        this.tutorProfileId = tutorProfileId;
        this.settlementYear = settlementYear;
        this.settlementMonth = settlementMonth;
        this.grossAmountUsd = BigDecimal.ZERO.setScale(2);
        this.platformFeeAmountUsd = BigDecimal.ZERO.setScale(2);
        this.netAmountUsd = BigDecimal.ZERO.setScale(2);
        this.lessonCount = 0;
        this.status = MonthlySettlementStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static MonthlySettlement open(Long tutorProfileId, int settlementYear, int settlementMonth, Instant now) {
        return new MonthlySettlement(tutorProfileId, settlementYear, settlementMonth, now);
    }

    /**
     * Accumulate one earned lesson into the open rollup. Only valid while the
     * settlement is still OPEN; closed/finalized months are immutable.
     */
    public void addLesson(BigDecimal grossUsd, BigDecimal platformFeeUsd, BigDecimal netUsd, Instant now) {
        this.grossAmountUsd = this.grossAmountUsd.add(grossUsd);
        this.platformFeeAmountUsd = this.platformFeeAmountUsd.add(platformFeeUsd);
        this.netAmountUsd = this.netAmountUsd.add(netUsd);
        this.lessonCount += 1;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return status == MonthlySettlementStatus.OPEN;
    }

    /**
     * Move the settlement along its lifecycle (OPEN -> CLOSED -> FINALIZED ->
     * PAID). Admin-driven; only forward transitions are allowed.
     */
    public void changeStatus(MonthlySettlementStatus target, Instant now) {
        if (target == null) {
            throw new com.haru.common.exception.BusinessException(
                    com.haru.common.exception.ErrorCode.INVALID_REQUEST, "Settlement status is required.");
        }
        if (target.ordinal() <= this.status.ordinal()) {
            throw new com.haru.common.exception.BusinessException(
                    com.haru.common.exception.ErrorCode.INVALID_REQUEST,
                    "Settlement status can only move forward (current=" + this.status + ", target=" + target + ").");
        }
        this.status = target;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getTutorProfileId() {
        return tutorProfileId;
    }

    public int getSettlementYear() {
        return settlementYear;
    }

    public int getSettlementMonth() {
        return settlementMonth;
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

    public int getLessonCount() {
        return lessonCount;
    }

    public MonthlySettlementStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
