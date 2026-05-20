package com.haru.booking.api.dto;

import com.haru.booking.domain.Booking;
import com.haru.booking.domain.BookingStatus;

import java.time.Instant;

public record BookingResponse(
        Long id,
        Long studentUserId,
        Long tutorProfileId,
        Long scheduleSlotId,
        Integer lessonDurationMinutes,
        Instant startAt,
        Instant endAt,
        BookingStatus status,
        String cancelReason,
        Boolean joinAvailable
) {

    public static BookingResponse from(Booking booking, Instant now) {
        return new BookingResponse(
                booking.getId(),
                booking.getStudent().getId(),
                booking.getTutorProfile().getId(),
                booking.getScheduleSlot().getId(),
                booking.getLessonDurationMinutes(),
                booking.getStartAt(),
                booking.getEndAt(),
                booking.getStatus(),
                booking.getCancelReason(),
                booking.isJoinAvailable(now)
        );
    }
}
