package com.haru.settings.application;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.settings.domain.FeePolicy;
import com.haru.settings.domain.PlatformSettings;
import com.haru.settings.infra.PlatformSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PlatformSettingsService {

    private final PlatformSettingsRepository platformSettingsRepository;

    /**
     * Simple in-process cache of the active fee policy. Settings are append-only
     * and change rarely (admin-driven), so a single cached snapshot is enough.
     * {@link #refresh()} clears it after a settings change.
     */
    private final AtomicReference<FeePolicy> cachedFeePolicy = new AtomicReference<>();

    public PlatformSettingsService(PlatformSettingsRepository platformSettingsRepository) {
        this.platformSettingsRepository = platformSettingsRepository;
    }

    @Transactional(readOnly = true)
    public FeePolicy currentFeePolicy() {
        FeePolicy cached = cachedFeePolicy.get();
        if (cached != null) {
            return cached;
        }
        FeePolicy resolved = loadActiveSettings().toFeePolicy();
        cachedFeePolicy.compareAndSet(null, resolved);
        return resolved;
    }

    /** The full active settings row (for fields not in {@link FeePolicy}, e.g. promo window/cap). */
    @Transactional(readOnly = true)
    public PlatformSettings currentSettings() {
        return loadActiveSettings();
    }

    /**
     * Append a new active settings version with the supplied fee overrides (null
     * keeps the current value), deactivating the previous active row. Past
     * payments keep their own snapshot, so this never rewrites historical pricing.
     * Returns the new active row.
     */
    @Transactional
    public PlatformSettings updateFees(
            BigDecimal studentFeeRate,
            BigDecimal platformFeeRate,
            BigDecimal withdrawalFeeRatePaypal,
            BigDecimal withdrawalFeeRateBank,
            BigDecimal fivePackDiscountRate,
            BigDecimal tenPackDiscountRate,
            BigDecimal promoWithdrawalFeeRate,
            Integer cancelWindowHours,
            Integer creditExpiryMonths,
            Long updatedByAdminId
    ) {
        PlatformSettings current = loadActiveSettings();
        PlatformSettings next = current.nextVersion(
                studentFeeRate,
                platformFeeRate,
                withdrawalFeeRatePaypal,
                withdrawalFeeRateBank,
                fivePackDiscountRate,
                tenPackDiscountRate,
                promoWithdrawalFeeRate,
                cancelWindowHours,
                creditExpiryMonths,
                updatedByAdminId,
                java.time.Instant.now()
        );
        current.deactivate();
        PlatformSettings saved = platformSettingsRepository.save(next);
        refresh();
        return saved;
    }

    /**
     * Drop the cached fee policy so the next {@link #currentFeePolicy()} call
     * reloads the active row. Call after inserting a new settings version.
     */
    public void refresh() {
        cachedFeePolicy.set(null);
    }

    private PlatformSettings loadActiveSettings() {
        return platformSettingsRepository.findFirstByIsActiveTrueOrderBySettingVersionDesc()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "No active platform settings are configured."
                ));
    }
}
