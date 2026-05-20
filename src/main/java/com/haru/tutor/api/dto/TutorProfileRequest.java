package com.haru.tutor.api.dto;

import com.haru.tutor.domain.TutorCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record TutorProfileRequest(
        @Size(max = 100)
        String displayName,

        @Size(max = 255)
        String shortIntroduction,

        String aboutMe,

        String whatIOffer,

        TutorCategory category,

        @Size(max = 500)
        String profileImageUrl,

        @Size(max = 500)
        String introVideoUrl,

        @Size(max = 500)
        String thumbnailUrl,

        @Size(max = 10)
        List<@NotBlank @Size(max = 50) String> availableLanguages,

        @DecimalMin(value = "0.01", inclusive = true)
        BigDecimal lessonPrice25Amount,

        @DecimalMin(value = "0.01", inclusive = true)
        BigDecimal lessonPrice50Amount,

        @Size(max = 500)
        String availableTimeNote,

        @Size(max = 100)
        String paymentMethod
) {
}
