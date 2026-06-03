package com.haru.settlement.api.dto;

import com.haru.settlement.domain.PromoFeeWaiverGrant;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Promo fee waiver grant view. Field names follow the frontend money contract:
 * {@code granted} is the active flag; {@code waiverUntil} is the promo end date.
 */
public record PromoFeeWaiverResponse(
        Long id,
        Long tutorProfileId,
        boolean granted,
        LocalDate waiverUntil,
        Instant grantedAt,
        Instant revokedAt,
        String note
) {

    public static PromoFeeWaiverResponse from(PromoFeeWaiverGrant grant) {
        return new PromoFeeWaiverResponse(
                grant.getId(),
                grant.getTutorProfileId(),
                grant.isActive(),
                grant.getWaiverUntil(),
                grant.getGrantedAt(),
                grant.getRevokedAt(),
                null
        );
    }
}
