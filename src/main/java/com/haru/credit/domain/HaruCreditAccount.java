package com.haru.credit.domain;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * One Haru credit account per user. Holds the running USD balance of the
 * student's refund-only virtual currency. Expiry is tracked by a single account
 * level rule: {@code expiresAt = lastActivityAt + creditExpiryMonths} (the lot
 * level expiry is intentionally not modelled — see money model design §7). Any
 * activity (issuance or spend) pushes {@code lastActivityAt}/{@code expiresAt}
 * forward.
 */
@Entity
@Table(name = "haru_credit_accounts")
public class HaruCreditAccount {

    public static final String CURRENCY = "USD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "balance_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceUsd;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HaruCreditAccount() {
    }

    private HaruCreditAccount(Long userId, int creditExpiryMonths, Instant now) {
        this.userId = userId;
        this.balanceUsd = BigDecimal.ZERO.setScale(2);
        this.currency = CURRENCY;
        this.lastActivityAt = now;
        this.expiresAt = now.plus(monthsToDays(creditExpiryMonths), ChronoUnit.DAYS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static HaruCreditAccount open(Long userId, int creditExpiryMonths, Instant now) {
        return new HaruCreditAccount(userId, creditExpiryMonths, now);
    }

    /** Issue (+) credit, e.g. from an approved refund. Refreshes the expiry clock. */
    public void issue(BigDecimal amountUsd, int creditExpiryMonths, Instant now) {
        if (amountUsd == null || amountUsd.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Credit issue amount must be positive.");
        }
        this.balanceUsd = this.balanceUsd.add(amountUsd);
        touchActivity(creditExpiryMonths, now);
    }

    /** Spend (-) credit FIFO. Refreshes the expiry clock. */
    public void spend(BigDecimal amountUsd, int creditExpiryMonths, Instant now) {
        if (amountUsd == null || amountUsd.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Credit spend amount must be positive.");
        }
        if (this.balanceUsd.compareTo(amountUsd) < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Insufficient Haru credit balance.");
        }
        this.balanceUsd = this.balanceUsd.subtract(amountUsd);
        touchActivity(creditExpiryMonths, now);
    }

    /** Expire the whole remaining balance to zero. Returns the amount expired. */
    public BigDecimal expireAll(Instant now) {
        BigDecimal expired = this.balanceUsd;
        this.balanceUsd = BigDecimal.ZERO.setScale(2);
        this.updatedAt = now;
        return expired;
    }

    public boolean isExpired(Instant now) {
        return balanceUsd.signum() > 0 && !now.isBefore(expiresAt);
    }

    private void touchActivity(int creditExpiryMonths, Instant now) {
        this.lastActivityAt = now;
        this.expiresAt = now.plus(monthsToDays(creditExpiryMonths), ChronoUnit.DAYS);
        this.updatedAt = now;
    }

    private static long monthsToDays(int months) {
        // Account-level expiry tracked in days (Instant has no month unit). Uses a
        // 30-day month approximation, sufficient for the 12-month inactivity rule.
        return (long) months * 30L;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getBalanceUsd() {
        return balanceUsd;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
