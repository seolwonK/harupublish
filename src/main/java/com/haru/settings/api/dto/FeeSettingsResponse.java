package com.haru.settings.api.dto;

import com.haru.settings.domain.PlatformSettings;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fee/policy view. Field names follow the frontend money contract. PayPal and
 * Payoneer share one stored rate (both 3%), surfaced as two fields for the UI.
 * {@code id} carries the append-only settings version.
 */
public record FeeSettingsResponse(
        int id,
        BigDecimal studentFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal fivePackDiscountRate,
        BigDecimal tenPackDiscountRate,
        BigDecimal withdrawalFeeRatePaypal,
        BigDecimal withdrawalFeeRatePayoneer,
        BigDecimal withdrawalFeeRateDomestic,
        BigDecimal promoWithdrawalFeeRate,
        int cancellationCutoffHours,
        int creditExpiryMonths,
        Instant updatedAt,
        Long updatedBy
) {

    public static FeeSettingsResponse from(PlatformSettings settings) {
        return new FeeSettingsResponse(
                settings.getSettingVersion(),
                settings.getStudentFeeRate(),
                settings.getPlatformFeeRate(),
                settings.getFivePackDiscountRate(),
                settings.getTenPackDiscountRate(),
                settings.getWithdrawalFeeRatePaypal(),
                settings.getWithdrawalFeeRatePaypal(),
                settings.getWithdrawalFeeRateBank(),
                settings.getPromoWithdrawalFeeRate(),
                settings.getCancelWindowHours(),
                settings.getCreditExpiryMonths(),
                settings.getCreatedAt(),
                settings.getUpdatedByAdminId()
        );
    }
}
