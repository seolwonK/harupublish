package com.haru.dev.api.dto;

import java.time.Instant;

/** A fully booked lesson created by the dev tooling (slot + PAID payment + booking). */
public record CreatedLessonResponse(
        Long bookingId,
        Long scheduleSlotId,
        Instant startAt,
        Instant endAt,
        String status
) {
}
