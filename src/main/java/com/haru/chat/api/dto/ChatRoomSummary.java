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
        long unreadCount,
        /** 상대가 어디까지 읽었는지 — 내 메시지의 '읽음' 표시용 (시스템 방은 null). */
        Long counterpartLastReadMessageId
) {
}
