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
        Double averageRating,
        Integer reviewCount
) {

    public static ExpertListResponse from(TutorProfile profile, Double averageRating, Integer reviewCount) {
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
                averageRating,
                reviewCount
        );
    }
}
