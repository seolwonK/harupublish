package com.haru.chat.api.dto;

import java.util.List;

/** Messages are ordered oldest → newest within the page. */
public record ChatMessageListResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore
) {
}
