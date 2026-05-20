package com.haru.schedule.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TutorScheduleRequest(
        @NotNull
        @Size(max = 200)
        List<@Valid ScheduleSlotRequest> slots
) {
}
