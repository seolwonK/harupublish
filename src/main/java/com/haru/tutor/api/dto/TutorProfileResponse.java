package com.haru.tutor.api.dto;

import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorProfileStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TutorProfileResponse(
        Long id,
        Long userId,
        String displayName,
        String shortIntroduction,
        String aboutMe,
        String whatIOffer,
        String category,
        String profileImageUrl,
        String introVideoUrl,
        String thumbnailUrl,
        String availableLanguages,
        BigDecimal lessonPriceAmount,
        String availableTimeNote,
        String paymentMethod,
        TutorProfileStatus status,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static TutorProfileResponse from(TutorProfile profile) {
        return new TutorProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getDisplayName(),
                profile.getShortIntroduction(),
                profile.getAboutMe(),
                profile.getWhatIOffer(),
                profile.getCategory(),
                profile.getProfileImageUrl(),
                profile.getIntroVideoUrl(),
                profile.getThumbnailUrl(),
                profile.getAvailableLanguages(),
                profile.getLessonPriceAmount(),
                profile.getAvailableTimeNote(),
                profile.getPaymentMethod(),
                profile.getStatus(),
                profile.getSubmittedAt(),
                profile.getApprovedAt(),
                profile.getRejectedAt(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
