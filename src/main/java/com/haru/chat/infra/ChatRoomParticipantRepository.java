package com.haru.chat.infra;

import com.haru.chat.domain.ChatRoomParticipant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, Long> {

    Optional<ChatRoomParticipant> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<ChatRoomParticipant> findAllByChatRoomId(Long chatRoomId);

    /** All membership rows of the rooms a user participates in, newest room first. */
    @EntityGraph(attributePaths = {"chatRoom"})
    @Query("""
            select p
            from ChatRoomParticipant p
            where p.user.id = :userId
            order by p.chatRoom.updatedAt desc
            """)
    List<ChatRoomParticipant> findMembershipsForUser(@Param("userId") Long userId);

    /** Participant rows (with users) of every room the given user belongs to. */
    @EntityGraph(attributePaths = {"user"})
    @Query("""
            select p
            from ChatRoomParticipant p
            where p.chatRoom.id in (
                select mine.chatRoom.id from ChatRoomParticipant mine where mine.user.id = :userId
            )
            """)
    List<ChatRoomParticipant> findParticipantsOfUserRooms(@Param("userId") Long userId);
}
