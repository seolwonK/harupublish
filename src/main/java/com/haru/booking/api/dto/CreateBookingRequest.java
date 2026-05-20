package com.haru.booking.api.dto;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull
        Long tutorProfileId,

        @NotNull
        Long scheduleSlotId,

        @NotNull
        Integer lessonDurationMinutes
) {
}
