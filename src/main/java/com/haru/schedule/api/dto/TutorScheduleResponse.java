package com.haru.schedule.api.dto;

import com.haru.schedule.domain.TutorScheduleSlot;

import java.util.List;

public record TutorScheduleResponse(
        List<ScheduleSlotResponse> slots
) {

    public static TutorScheduleResponse from(List<TutorScheduleSlot> slots) {
        return new TutorScheduleResponse(
                slots.stream()
                        .map(ScheduleSlotResponse::from)
                        .toList()
        );
    }
}
