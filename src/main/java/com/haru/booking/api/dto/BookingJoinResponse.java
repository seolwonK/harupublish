package com.haru.booking.api.dto;

import com.haru.meeting.application.JitsiJoinPayload;

import java.time.Instant;

public record BookingJoinResponse(
        Long bookingId,
        Boolean joinAvailable,
        String joinUrl,
        String message,
        String provider,
        String domain,
        String roomName,
        String jwt,
        String externalUrl,
        Instant expiresAt
) {

    public static BookingJoinResponse unavailable(Long bookingId, String message) {
        return new BookingJoinResponse(bookingId, false, null, message, null, null, null, null, null, null);
    }

    public static BookingJoinResponse available(Long bookingId, JitsiJoinPayload payload) {
        return new BookingJoinResponse(
                bookingId,
                true,
                payload.externalUrl(),
                "Lesson room is ready.",
                payload.provider(),
                payload.domain(),
                payload.roomName(),
                payload.jwt(),
                payload.externalUrl(),
                payload.expiresAt()
        );
    }
}
