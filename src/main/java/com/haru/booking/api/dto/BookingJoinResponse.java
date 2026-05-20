package com.haru.booking.api.dto;

public record BookingJoinResponse(
        Long bookingId,
        Boolean joinAvailable,
        String joinUrl,
        String message
) {
}
