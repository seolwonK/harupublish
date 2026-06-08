package com.haru.chat.api.dto;

import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(
        @NotNull(message = "lastMessageId is required.")
        Long lastMessageId
) {
}
