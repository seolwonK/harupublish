package com.haru.chat.domain;

import com.haru.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public static final int MAX_BODY_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    /** Null for system (bot) messages. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private UserAccount sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    @Column(length = MAX_BODY_LENGTH)
    private String body;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "attachment_content_type", length = 100)
    private String attachmentContentType;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    private ChatMessage(ChatRoom chatRoom, UserAccount sender, MessageType messageType, String body) {
        this.chatRoom = chatRoom;
        this.sender = sender;
        this.messageType = messageType;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public static ChatMessage text(ChatRoom chatRoom, UserAccount sender, String body) {
        return new ChatMessage(chatRoom, sender, MessageType.TEXT, body);
    }

    public static ChatMessage system(ChatRoom chatRoom, String body) {
        return new ChatMessage(chatRoom, null, MessageType.SYSTEM, body);
    }

    public static ChatMessage attachment(
            ChatRoom chatRoom,
            UserAccount sender,
            MessageType messageType,
            String attachmentUrl,
            String attachmentName,
            String attachmentContentType,
            Long attachmentSize
    ) {
        ChatMessage message = new ChatMessage(chatRoom, sender, messageType, null);
        message.attachmentUrl = attachmentUrl;
        message.attachmentName = attachmentName;
        message.attachmentContentType = attachmentContentType;
        message.attachmentSize = attachmentSize;
        return message;
    }

    public Long getId() {
        return id;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public UserAccount getSender() {
        return sender;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public String getBody() {
        return body;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public String getAttachmentContentType() {
        return attachmentContentType;
    }

    public Long getAttachmentSize() {
        return attachmentSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
