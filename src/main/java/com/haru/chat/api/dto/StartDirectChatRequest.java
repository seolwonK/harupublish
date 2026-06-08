package com.haru.chat.api.dto;

import jakarta.validation.constraints.NotNull;

/** Start (or reuse) a 1:1 chat with another user picked from contact search. */
public record StartDirectChatRequest(
        @NotNull(message = "userId is required.")
        Long userId
) {
}
