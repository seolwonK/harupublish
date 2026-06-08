package com.haru.settlement.application;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.NotFoundException;
import com.haru.settings.application.PlatformSettingsService;
import com.haru.settings.domain.PlatformSettings;
import com.haru.settlement.domain.PromoFeeWaiverGrant;
import com.haru.settlement.infra.PromoFeeWaiverGrantRepository;
import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.infra.TutorProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin management of the initial promo platform-fee waiver (first N tutors free
 * until year-end). Enforces the configured cap of concurrently-active grants so
 * the promo cannot exceed its budget.
 */
@Service
public class PromoFeeWaiverService {

    private final PromoFeeWaiverGrantRepository promoFeeWaiverGrantRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final PlatformSettingsService platformSettingsService;

    public PromoFeeWaiverService(
            PromoFeeWaiverGrantRepository promoFeeWaiverGrantRepository,
            TutorProfileRepository tutorProfileRepository,
            PlatformSettingsService platformSettingsService
    ) {
        this.promoFeeWaiverGrantRepository = promoFeeWaiverGrantRepository;
        this.tutorProfileRepository = tutorProfileRepository;
        this.platformSettingsService = platformSettingsService;
    }

    @Transactional
    public PromoFeeWaiverGrant grant(Long tutorProfileId, Long adminUserId) {
        TutorProfile tutorProfile = tutorProfileRepository.findById(tutorProfileId)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
        PlatformSettings settings = platformSettingsService.currentSettings();
        LocalDate waiverUntil = settings.getPromoFeeWaiverUntil();
        int maxWaived = settings.getPromoMaxWaivedTutors();
        Instant now = Instant.now();

        PromoFeeWaiverGrant grant = promoFeeWaiverGrantRepository.findByTutorProfileId(tutorProfile.getId())
                .orElse(null);
        if (grant != null && grant.isActive()) {
            // Already granted — idempotent re-grant of the same tutor.
            return grant;
        }

        long activeCount = promoFeeWaiverGrantRepository.countByActiveTrue();
        if (activeCount >= maxWaived) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Promo fee waiver cap reached (" + maxWaived + " active grants)."
            );
        }

        if (grant == null) {
            grant = PromoFeeWaiverGrant.grant(tutorProfile.getId(), waiverUntil, adminUserId, now);
            return promoFeeWaiverGrantRepository.save(grant);
        }
        grant.regrant(waiverUntil, adminUserId, now);
        return grant;
    }

    @Transactional
    public void revoke(Long tutorProfileId, Long adminUserId) {
        PromoFeeWaiverGrant grant = promoFeeWaiverGrantRepository.findByTutorProfileId(tutorProfileId)
                .orElseThrow(() -> new NotFoundException("Promo fee waiver grant was not found."));
        grant.revoke(adminUserId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PromoFeeWaiverGrant> listActive() {
        return promoFeeWaiverGrantRepository.findAllByActiveTrue();
    }
}
