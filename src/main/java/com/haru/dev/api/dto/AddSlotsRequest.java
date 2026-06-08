package com.haru.dev.api.dto;

import java.time.Instant;
import java.util.List;

/** Add open schedule slots to a tutor, by UTC start instant (floored to 30-min boundaries). */
public record AddSlotsRequest(
        List<Instant> startAts
) {
}
