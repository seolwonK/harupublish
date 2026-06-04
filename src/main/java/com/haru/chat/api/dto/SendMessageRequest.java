package com.haru.chat.api.dto;

import com.haru.chat.domain.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Message body is required.")
        @Size(max = ChatMessage.MAX_BODY_LENGTH, message = "Message body must be 2000 characters or fewer.")
        String body
) {
}
