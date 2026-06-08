package com.haru.tutor.api.dto;

import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorCategory;
import com.haru.tutor.domain.TutorProfileStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TutorProfileResponse(
        Long id,
        Long userId,
        String displayName,
        String shortIntroduction,
        String aboutMe,
        String whatIOffer,
        TutorCategory category,
        String profileImageUrl,
        String introVideoUrl,
        String thumbnailUrl,
        List<String> availableLanguages,
        BigDecimal lessonPrice25Amount,
        BigDecimal lessonPrice50Amount,
        // Student-facing 25-min price = lessonPrice25Amount * (1 + studentFeeRate),
        // HALF_UP scale 2 (#9). Display value only — the authoritative price is the
        // checkout response. May be null if no fee policy is configured.
        BigDecimal studentPrice25Amount,
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
        return from(profile, null);
    }

    public static TutorProfileResponse from(TutorProfile profile, BigDecimal studentPrice25Amount) {
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
                profile.getLessonPrice25Amount(),
                profile.getLessonPrice50Amount(),
                studentPrice25Amount,
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
