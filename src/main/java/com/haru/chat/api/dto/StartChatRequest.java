package com.haru.chat.api.dto;

import jakarta.validation.constraints.NotNull;

public record StartChatRequest(
        @NotNull(message = "tutorProfileId is required.")
        Long tutorProfileId
) {
}
