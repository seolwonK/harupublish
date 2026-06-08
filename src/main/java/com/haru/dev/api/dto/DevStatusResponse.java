package com.haru.dev.api.dto;

/** Reachable only when dev tooling is enabled; lets the frontend probe availability. */
public record DevStatusResponse(
        boolean enabled
) {
}
