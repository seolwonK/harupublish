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
}
