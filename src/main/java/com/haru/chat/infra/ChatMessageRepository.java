package com.haru.chat.infra;

import com.haru.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = {"sender"})
    List<ChatMessage> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId, Long beforeId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    List<ChatMessage> findByIdIn(List<Long> ids);

    /**
     * Messages another participant sent after the reader's last-read pointer.
     * System messages (sender = null) also count as unread.
     */
    @Query("""
            select count(m)
            from ChatMessage m
            where m.chatRoom.id = :roomId
              and m.id > :lastReadId
              and (m.sender is null or m.sender.id <> :userId)
            """)
    long countUnread(@Param("roomId") Long roomId, @Param("lastReadId") Long lastReadId, @Param("userId") Long userId);

    /** Per-room unread counts for every room the user participates in — one query. */
    @Query("""
            select p.chatRoom.id, count(m)
            from ChatRoomParticipant p
            join ChatMessage m on m.chatRoom.id = p.chatRoom.id
            where p.user.id = :userId
              and m.id > coalesce(p.lastReadMessageId, 0)
              and (m.sender is null or m.sender.id <> :userId)
            group by p.chatRoom.id
            """)
    List<Object[]> countUnreadByRoomForUser(@Param("userId") Long userId);

    /** Total unread across all of the user's rooms — one query (header badge). */
    @Query("""
            select count(m)
            from ChatRoomParticipant p
            join ChatMessage m on m.chatRoom.id = p.chatRoom.id
            where p.user.id = :userId
              and m.id > coalesce(p.lastReadMessageId, 0)
              and (m.sender is null or m.sender.id <> :userId)
            """)
    long countTotalUnreadForUser(@Param("userId") Long userId);
}
