package com.haru.tutor.api.dto;

import com.haru.tutor.domain.TutorProfile;

import java.math.BigDecimal;

public record ExpertListResponse(
        Long tutorProfileId,
        Long userId,
        String displayName,
        String shortIntroduction,
        String category,
        String profileImageUrl,
        String thumbnailUrl,
        String availableLanguages,
        BigDecimal lessonPriceAmount
) {

    public static ExpertListResponse from(TutorProfile profile) {
        return new ExpertListResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getDisplayName(),
                profile.getShortIntroduction(),
                profile.getCategory(),
                profile.getProfileImageUrl(),
                profile.getThumbnailUrl(),
                profile.getAvailableLanguages(),
                profile.getLessonPriceAmount()
        );
    }
}
