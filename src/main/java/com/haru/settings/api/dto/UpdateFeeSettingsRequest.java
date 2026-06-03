package com.haru.settings.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Partial fee update — any null field keeps the current active value. Rates are
 * fractions (0.10 = 10%). Field names follow the frontend money contract. PayPal
 * and Payoneer share one stored rate; if both are supplied, {@code paypal} wins.
 * Promo enablement/window/cap are managed separately.
 */
public record UpdateFeeSettingsRequest(
        @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal studentFeeRate,
        @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal platformFeeRate,
        @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal fivePackDiscountRate,
        @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal tenPackDiscountRate,
        @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal withdrawalFeeRatePaypal,
        @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal withdrawalFeeRatePayoneer,
        @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal withdrawalFeeRateDomestic,
        @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal promoWithdrawalFeeRate,
        @Min(0)
        Integer cancellationCutoffHours,
        @Min(1)
        Integer creditExpiryMonths
) {

    /** The shared PayPal/Payoneer rate to apply (PayPal wins if both present). */
    public BigDecimal resolvedPaypalRate() {
        return withdrawalFeeRatePaypal != null ? withdrawalFeeRatePaypal : withdrawalFeeRatePayoneer;
    }
}
