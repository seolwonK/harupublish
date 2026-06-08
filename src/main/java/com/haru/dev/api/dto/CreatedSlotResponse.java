package com.haru.dev.api.dto;

import java.time.Instant;

/** An open schedule slot created by the dev tooling. */
public record CreatedSlotResponse(
        Long scheduleSlotId,
        Instant startAt,
        Instant endAt
) {
}
