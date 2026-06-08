package com.haru.settlement.domain;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * A tutor's cash-out request against their USD "cyber money" balance.
 *
 * <p>On request the gross amount is held (debited) on the earning ledger to
 * prevent double-withdrawal. The fee depends on the rail (PayPal/Payoneer 3%,
 * domestic bank 5%). Promo-waiver tutors do NOT get the withdrawal fee waived —
 * they pay a fixed {@code promoWithdrawalFeeRate} (5%) that is flagged for a
 * later payback ({@code promoPaybackPending}). The status machine is
 * REQUESTED -> APPROVED -> PAID, with REJECT reversing the held amount.</p>
 */
@Entity
@Table(name = "withdrawals")
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tutor_profile_id", nullable = false)
    private Long tutorProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private WithdrawalMethod method;

    @Column(name = "requested_amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmountUsd;

    @Column(name = "fee_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal feeRate;

    @Column(name = "fee_amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmountUsd;

    @Column(name = "net_payout_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPayoutUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WithdrawalStatus status;

    @Column(name = "promo_fee_waived", nullable = false)
    private boolean promoFeeWaived;

    @Column(name = "promo_payback_pending", nullable = false)
    private boolean promoPaybackPending;

    @Column(name = "promo_payback_amount_usd", precision = 12, scale = 2)
    private BigDecimal promoPaybackAmountUsd;

    @Column(name = "payout_reference", length = 200)
    private String payoutReference;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Withdrawal() {
    }

    private Withdrawal(
            Long tutorProfileId,
            WithdrawalMethod method,
            BigDecimal requestedAmountUsd,
            BigDecimal feeRate,
            boolean promoFeeWaiver,
            String payoutAccount,
            Instant now
    ) {
        this.tutorProfileId = tutorProfileId;
        this.method = method;
        this.requestedAmountUsd = money(requestedAmountUsd);
        this.feeRate = feeRate;
        this.feeAmountUsd = money(this.requestedAmountUsd.multiply(feeRate));
        this.netPayoutUsd = this.requestedAmountUsd.subtract(this.feeAmountUsd);
        this.status = WithdrawalStatus.REQUESTED;
        // Promo tutors still pay the (5%) withdrawal fee, but it is flagged for
        // payback. The fee is never "waived" at request time — only paid back later.
        this.promoFeeWaived = promoFeeWaiver;
        this.promoPaybackPending = promoFeeWaiver;
        this.promoPaybackAmountUsd = promoFeeWaiver ? this.feeAmountUsd : null;
        // The tutor's payout target (PayPal email / Payoneer / bank account); the
        // admin may overwrite it with a settlement reference on "paid".
        this.payoutReference = payoutAccount;
        this.requestedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Withdrawal request(
            Long tutorProfileId,
            WithdrawalMethod method,
            BigDecimal requestedAmountUsd,
            BigDecimal feeRate,
            boolean promoFeeWaiver,
            String payoutAccount,
            Instant now
    ) {
        if (requestedAmountUsd == null || requestedAmountUsd.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Withdrawal amount must be greater than zero.");
        }
        if (method == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Withdrawal method is required.");
        }
        return new Withdrawal(tutorProfileId, method, requestedAmountUsd, feeRate, promoFeeWaiver, payoutAccount, now);
    }

    public void approve(Instant now) {
        if (status != WithdrawalStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only requested withdrawals can be approved.");
        }
        this.status = WithdrawalStatus.APPROVED;
        this.approvedAt = now;
        touch(now);
    }

    public void markPaid(String payoutReference, Instant now) {
        if (status != WithdrawalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only approved withdrawals can be marked paid.");
        }
        this.status = WithdrawalStatus.PAID;
        if (payoutReference != null && !payoutReference.isBlank()) {
            this.payoutReference = payoutReference;
        }
        this.paidAt = now;
        touch(now);
    }

    public void reject(String reason, Instant now) {
        if (status != WithdrawalStatus.REQUESTED && status != WithdrawalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only requested or approved withdrawals can be rejected.");
        }
        this.status = WithdrawalStatus.REJECTED;
        this.rejectReason = reason;
        this.rejectedAt = now;
        // A rejected withdrawal never pays out, so there is nothing to pay back.
        this.promoPaybackPending = false;
        touch(now);
    }

    /** Mark the promo fee payback as settled once the ADJUSTMENT credit is written. */
    public void markPaybackSettled(Instant now) {
        this.promoPaybackPending = false;
        touch(now);
    }

    public boolean isHoldActive() {
        // The hold (debit) stays in effect for any non-rejected state.
        return status != WithdrawalStatus.REJECTED;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getTutorProfileId() {
        return tutorProfileId;
    }

    public WithdrawalMethod getMethod() {
        return method;
    }

    public BigDecimal getRequestedAmountUsd() {
        return requestedAmountUsd;
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    public BigDecimal getFeeAmountUsd() {
        return feeAmountUsd;
    }

    public BigDecimal getNetPayoutUsd() {
        return netPayoutUsd;
    }

    public WithdrawalStatus getStatus() {
        return status;
    }

    public boolean isPromoFeeWaived() {
        return promoFeeWaived;
    }

    public boolean isPromoPaybackPending() {
        return promoPaybackPending;
    }

    public BigDecimal getPromoPaybackAmountUsd() {
        return promoPaybackAmountUsd;
    }

    public String getPayoutReference() {
        return payoutReference;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
