package com.haru.chat.api.dto;

import com.haru.chat.domain.ChatMessage;
import com.haru.chat.domain.MessageType;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long chatRoomId,
        Long senderUserId,
        String senderName,
        MessageType messageType,
        String body,
        String attachmentUrl,
        String attachmentName,
        String attachmentContentType,
        Long attachmentSize,
        Instant createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoom().getId(),
                message.getSender() == null ? null : message.getSender().getId(),
                message.getSender() == null ? "Haru" : message.getSender().getName(),
                message.getMessageType(),
                message.getBody(),
                message.getAttachmentUrl(),
                message.getAttachmentName(),
                message.getAttachmentContentType(),
                message.getAttachmentSize(),
                message.getCreatedAt()
        );
    }
}
