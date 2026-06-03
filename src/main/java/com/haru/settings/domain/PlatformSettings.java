package com.haru.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only runtime configuration singleton. The currently effective row has
 * {@code isActive = true}; settings changes insert a new versioned row and flip
 * the previous active row off. Past payments keep their own snapshot, so editing
 * settings never rewrites historical pricing.
 */
@Entity
@Table(name = "platform_settings")
public class PlatformSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_version", nullable = false)
    private int settingVersion;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "student_fee_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal studentFeeRate;

    @Column(name = "platform_fee_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal platformFeeRate;

    @Column(name = "withdrawal_fee_rate_paypal", nullable = false, precision = 6, scale = 4)
    private BigDecimal withdrawalFeeRatePaypal;

    @Column(name = "withdrawal_fee_rate_bank", nullable = false, precision = 6, scale = 4)
    private BigDecimal withdrawalFeeRateBank;

    @Column(name = "five_pack_discount_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal fivePackDiscountRate;

    @Column(name = "ten_pack_discount_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal tenPackDiscountRate;

    @Column(name = "cancel_window_hours", nullable = false)
    private int cancelWindowHours;

    @Column(name = "credit_expiry_months", nullable = false)
    private int creditExpiryMonths;

    @Column(name = "promo_fee_waiver_enabled", nullable = false)
    private boolean promoFeeWaiverEnabled;

    @Column(name = "promo_fee_waiver_until")
    private LocalDate promoFeeWaiverUntil;

    @Column(name = "promo_max_waived_tutors", nullable = false)
    private int promoMaxWaivedTutors;

    @Column(name = "promo_withdrawal_fee_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal promoWithdrawalFeeRate;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "updated_by_admin_id")
    private Long updatedByAdminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlatformSettings() {
    }

    /**
     * Build the next active settings version from this (current) row, applying
     * the provided overrides. Null overrides keep the current value. The caller
     * is responsible for deactivating the previous row.
     */
    public PlatformSettings nextVersion(
            BigDecimal studentFeeRate,
            BigDecimal platformFeeRate,
            BigDecimal withdrawalFeeRatePaypal,
            BigDecimal withdrawalFeeRateBank,
            BigDecimal fivePackDiscountRate,
            BigDecimal tenPackDiscountRate,
            BigDecimal promoWithdrawalFeeRate,
            Integer cancelWindowHours,
            Integer creditExpiryMonths,
            Long updatedByAdminId,
            Instant now
    ) {
        PlatformSettings next = new PlatformSettings();
        next.settingVersion = this.settingVersion + 1;
        next.isActive = true;
        next.studentFeeRate = studentFeeRate != null ? studentFeeRate : this.studentFeeRate;
        next.platformFeeRate = platformFeeRate != null ? platformFeeRate : this.platformFeeRate;
        next.withdrawalFeeRatePaypal = withdrawalFeeRatePaypal != null ? withdrawalFeeRatePaypal : this.withdrawalFeeRatePaypal;
        next.withdrawalFeeRateBank = withdrawalFeeRateBank != null ? withdrawalFeeRateBank : this.withdrawalFeeRateBank;
        next.fivePackDiscountRate = fivePackDiscountRate != null ? fivePackDiscountRate : this.fivePackDiscountRate;
        next.tenPackDiscountRate = tenPackDiscountRate != null ? tenPackDiscountRate : this.tenPackDiscountRate;
        next.promoWithdrawalFeeRate = promoWithdrawalFeeRate != null ? promoWithdrawalFeeRate : this.promoWithdrawalFeeRate;
        next.cancelWindowHours = cancelWindowHours != null ? cancelWindowHours : this.cancelWindowHours;
        next.creditExpiryMonths = creditExpiryMonths != null ? creditExpiryMonths : this.creditExpiryMonths;
        // Promo waiver enablement/window/cap are not editable from the fees endpoint.
        next.promoFeeWaiverEnabled = this.promoFeeWaiverEnabled;
        next.promoFeeWaiverUntil = this.promoFeeWaiverUntil;
        next.promoMaxWaivedTutors = this.promoMaxWaivedTutors;
        next.baseCurrency = this.baseCurrency;
        next.updatedByAdminId = updatedByAdminId;
        next.createdAt = now;
        return next;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public FeePolicy toFeePolicy() {
        return new FeePolicy(
                studentFeeRate,
                platformFeeRate,
                withdrawalFeeRatePaypal,
                withdrawalFeeRateBank,
                fivePackDiscountRate,
                tenPackDiscountRate,
                promoWithdrawalFeeRate,
                cancelWindowHours,
                creditExpiryMonths,
                settingVersion
        );
    }

    public Long getId() {
        return id;
    }

    public int getSettingVersion() {
        return settingVersion;
    }

    public boolean isActive() {
        return isActive;
    }

    public BigDecimal getStudentFeeRate() {
        return studentFeeRate;
    }

    public BigDecimal getPlatformFeeRate() {
        return platformFeeRate;
    }

    public BigDecimal getWithdrawalFeeRatePaypal() {
        return withdrawalFeeRatePaypal;
    }

    public BigDecimal getWithdrawalFeeRateBank() {
        return withdrawalFeeRateBank;
    }

    public BigDecimal getFivePackDiscountRate() {
        return fivePackDiscountRate;
    }

    public BigDecimal getTenPackDiscountRate() {
        return tenPackDiscountRate;
    }

    public int getCancelWindowHours() {
        return cancelWindowHours;
    }

    public int getCreditExpiryMonths() {
        return creditExpiryMonths;
    }

    public boolean isPromoFeeWaiverEnabled() {
        return promoFeeWaiverEnabled;
    }

    public LocalDate getPromoFeeWaiverUntil() {
        return promoFeeWaiverUntil;
    }

    public int getPromoMaxWaivedTutors() {
        return promoMaxWaivedTutors;
    }

    public BigDecimal getPromoWithdrawalFeeRate() {
        return promoWithdrawalFeeRate;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public Long getUpdatedByAdminId() {
        return updatedByAdminId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
