package com.haru.chat.api.dto;

import com.haru.chat.domain.ChatRoomType;

import java.time.Instant;

public record ChatRoomSummary(
        Long id,
        ChatRoomType roomType,
        Long counterpartUserId,
        String counterpartName,
        String counterpartImageUrl,
        String lastMessagePreview,
        Instant lastMessageAt,
        long unreadCount
) {
}
