package com.haru.tutor.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TutorProfileRequest(
        @Size(max = 100)
        String displayName,

        @Size(max = 255)
        String shortIntroduction,

        String aboutMe,

        String whatIOffer,

        @Size(max = 50)
        String category,

        @Size(max = 500)
        String profileImageUrl,

        @Size(max = 500)
        String introVideoUrl,

        @Size(max = 500)
        String thumbnailUrl,

        @Size(max = 255)
        String availableLanguages,

        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal lessonPriceAmount,

        @Size(max = 500)
        String availableTimeNote,

        @Size(max = 100)
        String paymentMethod
) {
}
