package com.haru.schedule.api.dto;

import com.haru.schedule.domain.TutorScheduleSlot;

import java.time.Instant;

public record ScheduleSlotResponse(
        Long id,
        Instant startAt,
        Instant endAt,
        boolean booked
) {

    public static ScheduleSlotResponse from(TutorScheduleSlot slot, boolean booked) {
        return new ScheduleSlotResponse(
                slot.getId(),
                slot.getStartAt(),
                slot.getEndAt(),
                booked
        );
    }
}
