package com.haru.chat.domain;

import com.haru.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_room_participants")
public class ChatRoomParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    /** Highest message id this participant has read. Null = nothing read yet. */
    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatRoomParticipant() {
    }

    private ChatRoomParticipant(ChatRoom chatRoom, UserAccount user) {
        this.chatRoom = chatRoom;
        this.user = user;
        this.createdAt = Instant.now();
    }

    public static ChatRoomParticipant of(ChatRoom chatRoom, UserAccount user) {
        return new ChatRoomParticipant(chatRoom, user);
    }

    /** Read pointers only move forward; stale client calls cannot rewind them. */
    public void markRead(Long messageId) {
        if (messageId == null) {
            return;
        }
        if (lastReadMessageId == null || messageId > lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
    }

    public Long getId() {
        return id;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public UserAccount getUser() {
        return user;
    }

    public Long getLastReadMessageId() {
        return lastReadMessageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
