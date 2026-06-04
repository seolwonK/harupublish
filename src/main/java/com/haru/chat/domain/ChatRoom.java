package com.haru.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_rooms")
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private ChatRoomType roomType;

    /**
     * Uniqueness key that prevents duplicate rooms under concurrent creation.
     * DIRECT: {@code "{minUserId}:{maxUserId}"}, OPS: {@code "ops:{userId}"},
     * SYSTEM_NOTICE: {@code "notice:{userId}"}.
     */
    @Column(name = "direct_pair_key", length = 50, unique = true)
    private String directPairKey;

    /** Denormalized id of the latest message, used for list previews. */
    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatRoom() {
    }

    private ChatRoom(ChatRoomType roomType, String directPairKey) {
        this.roomType = roomType;
        this.directPairKey = directPairKey;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static ChatRoom direct(Long userIdA, Long userIdB) {
        return new ChatRoom(ChatRoomType.DIRECT, directPairKey(userIdA, userIdB));
    }

    public static ChatRoom systemNotice(Long userId) {
        return new ChatRoom(ChatRoomType.SYSTEM_NOTICE, "notice:" + userId);
    }

    public static ChatRoom ops(Long userId) {
        return new ChatRoom(ChatRoomType.OPS, "ops:" + userId);
    }

    public static String directPairKey(Long userIdA, Long userIdB) {
        long min = Math.min(userIdA, userIdB);
        long max = Math.max(userIdA, userIdB);
        return min + ":" + max;
    }

    public void touch(Long lastMessageId, Instant now) {
        this.lastMessageId = lastMessageId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public ChatRoomType getRoomType() {
        return roomType;
    }

    public String getDirectPairKey() {
        return directPairKey;
    }

    public Long getLastMessageId() {
        return lastMessageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
