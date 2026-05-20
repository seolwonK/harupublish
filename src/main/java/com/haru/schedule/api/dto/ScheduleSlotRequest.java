package com.haru.schedule.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ScheduleSlotRequest(
        @NotNull
        Instant startAt
) {
}
