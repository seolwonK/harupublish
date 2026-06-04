package com.haru.chat.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope published to STOMP destinations.
 *
 * <ul>
 *   <li>{@code MESSAGE} — a new message in a room ({@code message} is set).</li>
 *   <li>{@code READ} — a participant moved their read pointer.</li>
 *   <li>{@code UNREAD} — per-user signal that a room gained unread messages.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatSocketEvent(
        String type,
        Long chatRoomId,
        ChatMessageResponse message,
        Long userId,
        Long lastReadMessageId
) {

    public static ChatSocketEvent message(ChatMessageResponse message) {
        return new ChatSocketEvent("MESSAGE", message.chatRoomId(), message, null, null);
    }

    public static ChatSocketEvent read(Long chatRoomId, Long userId, Long lastReadMessageId) {
        return new ChatSocketEvent("READ", chatRoomId, null, userId, lastReadMessageId);
    }

    public static ChatSocketEvent unread(Long chatRoomId) {
        return new ChatSocketEvent("UNREAD", chatRoomId, null, null, null);
    }
}
