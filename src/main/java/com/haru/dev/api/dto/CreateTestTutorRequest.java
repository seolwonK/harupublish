package com.haru.dev.api.dto;

import com.haru.tutor.domain.TutorCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Developer test-data request. Every field is optional — the service fills in
 * sensible defaults so a no-body POST still produces a fully approved tutor.
 *
 * @param availabilitySlots open (bookable) schedule slots to create, by UTC start instant.
 * @param bookedLessons     start instants to auto-create as fully booked lessons
 *                          (each: slot + PAID payment + booking with an auto student).
 * @param autoLessonCount   when {@code bookedLessons} is empty, auto-generate this many
 *                          booked lessons spaced 30 minutes apart starting tomorrow.
 */
public record CreateTestTutorRequest(
        String name,
        String email,
        String password,
        TutorCategory category,
        BigDecimal lessonPrice25Amount,
        BigDecimal lessonPrice50Amount,
        List<String> availableLanguages,
        List<Instant> availabilitySlots,
        List<Instant> bookedLessons,
        Integer autoLessonCount
) {
}
