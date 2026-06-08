package com.haru.tutor.api.dto;

import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorCategory;

import java.math.BigDecimal;
import java.util.List;

public record ExpertListResponse(
        Long tutorProfileId,
        Long userId,
        String displayName,
        String shortIntroduction,
        TutorCategory category,
        String profileImageUrl,
        String thumbnailUrl,
        List<String> availableLanguages,
        BigDecimal lessonPrice25Amount,
        BigDecimal lessonPrice50Amount,
        // Student-facing 25-min price = lessonPrice25Amount * (1 + studentFeeRate),
        // HALF_UP scale 2 (#9). Display value only — the authoritative price is the
        // checkout response. May be null if no fee policy is configured.
        BigDecimal studentPrice25Amount,
        Double averageRating,
        Integer reviewCount
) {

    public static ExpertListResponse from(
            TutorProfile profile,
            BigDecimal studentPrice25Amount,
            Double averageRating,
            Integer reviewCount
    ) {
        return new ExpertListResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getDisplayName(),
                profile.getShortIntroduction(),
                profile.getCategory(),
                profile.getProfileImageUrl(),
                profile.getThumbnailUrl(),
                profile.getAvailableLanguages(),
                profile.getLessonPrice25Amount(),
                profile.getLessonPrice50Amount(),
                studentPrice25Amount,
                averageRating,
                reviewCount
        );
    }
}
