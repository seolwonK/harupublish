package com.haru.meeting.application;

import java.time.Instant;

public record JitsiJoinPayload(
        String provider,
        String domain,
        String roomName,
        String jwt,
        String externalUrl,
        Instant expiresAt
) {
}
