package com.haru.dev.api.dto;

import java.util.List;

/**
 * Result of creating a test tutor and (optionally) its lessons.
 *
 * @param studentAccount the auto-created student that owns the booked lessons,
 *                       or null when no booked lessons were requested.
 */
public record CreateTestTutorResponse(
        Long tutorProfileId,
        TestAccountResponse tutorAccount,
        TestAccountResponse studentAccount,
        List<CreatedSlotResponse> openSlots,
        List<CreatedLessonResponse> bookedLessons
) {
}
