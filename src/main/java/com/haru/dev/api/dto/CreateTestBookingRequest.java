package com.haru.dev.api.dto;

import java.time.Instant;

/**
 * Create a single fully booked lesson for an approved tutor: makes the slot (if
 * absent), records a PAID payment, and reserves the booking.
 *
 * @param studentUserId existing student to book as; when null an auto test student is created.
 */
public record CreateTestBookingRequest(
        Long tutorProfileId,
        Instant startAt,
        Long studentUserId
) {
}
