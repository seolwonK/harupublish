package com.haru.booking.api.dto;

import com.haru.booking.domain.Booking;

import java.time.Instant;
import java.util.List;

public record BookingListResponse(
        List<BookingResponse> bookings
) {

    public static BookingListResponse from(List<Booking> bookings, Instant now) {
        return new BookingListResponse(
                bookings.stream()
                        .map(booking -> BookingResponse.from(booking, now))
                        .toList()
        );
    }
}
