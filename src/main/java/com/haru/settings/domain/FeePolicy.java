package com.haru.settings.domain;

import java.math.BigDecimal;

/**
 * Immutable snapshot of the runtime fee policy resolved from the active
 * {@link PlatformSettings} row. Injected into pricing logic so that the
 * calculation never reads static constants and every payment can record the
 * exact rates that were in effect at checkout time.
 */
public record FeePolicy(
        BigDecimal studentFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal withdrawalFeeRatePaypal,
        BigDecimal withdrawalFeeRateBank,
        BigDecimal fivePackDiscountRate,
        BigDecimal tenPackDiscountRate,
        BigDecimal promoWithdrawalFeeRate,
        int cancelWindowHours,
        int creditExpiryMonths,
        int settingVersion
) {

    /**
     * Pack-count based lesson discount rate, derived from this policy.
     * 5-pack and 10-pack share the discount with the tutor; everything else is 0.
     */
    public BigDecimal discountRate(int lessonPackCount) {
        if (lessonPackCount == 5) {
            return fivePackDiscountRate;
        }
        if (lessonPackCount == 10) {
            return tenPackDiscountRate;
        }
        return BigDecimal.ZERO;
    }
}
