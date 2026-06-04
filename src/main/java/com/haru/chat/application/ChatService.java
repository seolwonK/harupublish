package com.haru.chat.application;

import com.haru.chat.api.dto.ChatMessageListResponse;
import com.haru.chat.api.dto.ChatMessageResponse;
import com.haru.chat.api.dto.ChatRoomListResponse;
import com.haru.chat.api.dto.ChatRoomSummary;
import com.haru.chat.api.dto.SendMessageRequest;
import com.haru.chat.api.dto.StartChatRequest;
import com.haru.chat.domain.ChatMessage;
import com.haru.chat.domain.ChatRoom;
import com.haru.chat.domain.ChatRoomParticipant;
import com.haru.chat.domain.ChatRoomType;
import com.haru.chat.infra.ChatMessageRepository;
import com.haru.chat.infra.ChatRoomParticipantRepository;
import com.haru.chat.infra.ChatRoomRepository;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.ForbiddenException;
import com.haru.common.exception.NotFoundException;
import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.tutor.infra.TutorProfileRepository;
import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatService {

    static final String SYSTEM_NOTICE_ROOM_NAME = "Haru 알림";
    static final String OPS_ROOM_NAME = "운영팀";
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserAccountRepository userAccountRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatBroadcaster chatBroadcaster;
    private final ChatAttachmentService chatAttachmentService;

    public ChatService(
            UserAccountRepository userAccountRepository,
            TutorProfileRepository tutorProfileRepository,
            ChatRoomRepository chatRoomRepository,
            ChatRoomParticipantRepository participantRepository,
            ChatMessageRepository messageRepository,
            ChatBroadcaster chatBroadcaster,
            ChatAttachmentService chatAttachmentService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tutorProfileRepository = tutorProfileRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.chatBroadcaster = chatBroadcaster;
        this.chatAttachmentService = chatAttachmentService;
    }

    @Transactional
    public ChatRoomSummary startDirectRoom(Long requesterId, StartChatRequest request) {
        UserAccount requester = userAccountRepository.findWithRolesById(requesterId)
                .orElseThrow(() -> new NotFoundException("User was not found."));
        TutorProfile tutorProfile = tutorProfileRepository.findByIdAndStatusAndHiddenFalse(request.tutorProfileId(), TutorProfileStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
        UserAccount tutorUser = tutorProfile.getUser();
        if (tutorUser.getId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "You cannot start a chat with yourself.");
        }

        ChatRoom room = findOrCreateDirectRoom(requester, tutorUser);
        return summarize(room, requesterId, List.of(requester, tutorUser));
    }

    @Transactional
    public ChatRoomListResponse listMyRooms(Long userId) {
        UserAccount user = userAccountRepository.findWithRolesById(userId)
                .orElseThrow(() -> new NotFoundException("User was not found."));
        ensureSystemRooms(user);

        List<ChatRoomParticipant> memberships = participantRepository.findMembershipsForUser(userId);
        Map<Long, List<ChatRoomParticipant>> participantsByRoom = participantRepository.findParticipantsOfUserRooms(userId).stream()
                .collect(Collectors.groupingBy(participant -> participant.getChatRoom().getId()));

        Set<Long> counterpartUserIds = participantsByRoom.values().stream()
                .flatMap(List::stream)
                .map(participant -> participant.getUser().getId())
                .filter(id -> !id.equals(userId))
                .collect(Collectors.toSet());
        Map<Long, TutorProfile> tutorProfilesByUserId = counterpartUserIds.isEmpty()
                ? Map.of()
                : tutorProfileRepository.findAllByUserIdIn(counterpartUserIds).stream()
                        .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity(), (a, b) -> a));

        List<Long> lastMessageIds = memberships.stream()
                .map(membership -> membership.getChatRoom().getLastMessageId())
                .filter(Objects::nonNull)
                .toList();
        Map<Long, ChatMessage> lastMessagesById = lastMessageIds.isEmpty()
                ? Map.of()
                : messageRepository.findByIdIn(lastMessageIds).stream()
                        .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        List<ChatRoomSummary> summaries = new ArrayList<>();
        for (ChatRoomParticipant membership : memberships) {
            ChatRoom room = membership.getChatRoom();
            List<ChatRoomParticipant> roomParticipants = participantsByRoom.getOrDefault(room.getId(), List.of());
            UserAccount counterpart = roomParticipants.stream()
                    .map(ChatRoomParticipant::getUser)
                    .filter(participant -> !participant.getId().equals(userId))
                    .findFirst()
                    .orElse(null);
            ChatMessage lastMessage = room.getLastMessageId() == null ? null : lastMessagesById.get(room.getLastMessageId());
            summaries.add(toSummary(room, membership, counterpart, tutorProfilesByUserId, lastMessage, userId));
        }
        return new ChatRoomListResponse(summaries);
    }

    @Transactional(readOnly = true)
    public ChatMessageListResponse getMessages(Long userId, Long roomId, Long beforeId, Integer size) {
        requireParticipant(userId, roomId);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<ChatMessage> descending = beforeId == null
                ? messageRepository.findByChatRoomIdOrderByIdDesc(roomId, page)
                : messageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId, page);

        boolean hasMore = descending.size() > pageSize;
        List<ChatMessage> pageMessages = hasMore ? descending.subList(0, pageSize) : descending;
        List<ChatMessageResponse> ascending = new ArrayList<>(pageMessages.stream().map(ChatMessageResponse::from).toList());
        Collections.reverse(ascending);
        return new ChatMessageListResponse(ascending, hasMore);
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long senderId, Long roomId, SendMessageRequest request) {
        ChatRoomParticipant membership = requireParticipant(senderId, roomId);
        ChatRoom room = membership.getChatRoom();
        if (room.getRoomType() == ChatRoomType.SYSTEM_NOTICE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Messages cannot be sent to the notification room.");
        }

        ChatMessage message = messageRepository.saveAndFlush(
                ChatMessage.text(room, membership.getUser(), request.body().trim())
        );
        room.touch(message.getId(), Instant.now());
        ChatMessageResponse response = ChatMessageResponse.from(message);
        chatBroadcaster.broadcastMessage(participantUserIds(roomId), response);
        return response;
    }

    @Transactional
    public ChatMessageResponse sendAttachment(Long senderId, Long roomId, MultipartFile file) {
        ChatRoomParticipant membership = requireParticipant(senderId, roomId);
        ChatRoom room = membership.getChatRoom();
        if (room.getRoomType() == ChatRoomType.SYSTEM_NOTICE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Messages cannot be sent to the notification room.");
        }

        ChatAttachmentService.StoredAttachment stored = chatAttachmentService.store(roomId, file);
        ChatMessage message = messageRepository.saveAndFlush(ChatMessage.attachment(
                room,
                membership.getUser(),
                stored.messageType(),
                stored.url(),
                stored.originalName(),
                stored.contentType(),
                stored.size()
        ));
        room.touch(message.getId(), Instant.now());
        ChatMessageResponse response = ChatMessageResponse.from(message);
        chatBroadcaster.broadcastMessage(participantUserIds(roomId), response);
        return response;
    }

    private List<Long> participantUserIds(Long roomId) {
        return participantRepository.findAllByChatRoomId(roomId).stream()
                .map(participant -> participant.getUser().getId())
                .toList();
    }

    @Transactional
    public void markRead(Long userId, Long roomId, Long lastMessageId) {
        ChatRoomParticipant membership = requireParticipant(userId, roomId);
        Long before = membership.getLastReadMessageId();
        membership.markRead(lastMessageId);
        if (!Objects.equals(before, membership.getLastReadMessageId())) {
            chatBroadcaster.broadcastRead(roomId, userId, membership.getLastReadMessageId());
        }
    }

    /**
     * Drop a system (bot) message into the user's "Haru 알림" room, creating the
     * room on first use. Joins the caller's transaction so a rollback also
     * discards the notice; the broadcast itself is deferred to after commit.
     */
    @Transactional
    public void sendSystemNotice(Long userId, String body) {
        UserAccount user = userAccountRepository.findWithRolesById(userId).orElse(null);
        if (user == null || user.getRoles().contains(Role.ADMIN)) {
            return;
        }
        String key = "notice:" + user.getId();
        ChatRoom room = chatRoomRepository.findByDirectPairKey(key)
                .orElseGet(() -> createRoom(ChatRoom.systemNotice(user.getId()), key, user));
        ChatMessage message = messageRepository.saveAndFlush(ChatMessage.system(room, body));
        room.touch(message.getId(), Instant.now());
        chatBroadcaster.broadcastMessage(List.of(user.getId()), ChatMessageResponse.from(message));
    }

    @Transactional(readOnly = true)
    public long totalUnreadCount(Long userId) {
        return participantRepository.findMembershipsForUser(userId).stream()
                .filter(membership -> membership.getChatRoom().getLastMessageId() != null)
                .mapToLong(membership -> countUnread(
                        membership.getChatRoom().getId(),
                        membership.getLastReadMessageId(),
                        userId
                ))
                .sum();
    }

    private ChatRoom findOrCreateDirectRoom(UserAccount requester, UserAccount tutorUser) {
        String pairKey = ChatRoom.directPairKey(requester.getId(), tutorUser.getId());
        return chatRoomRepository.findByDirectPairKey(pairKey)
                .orElseGet(() -> createRoom(ChatRoom.direct(requester.getId(), tutorUser.getId()), pairKey, requester, tutorUser));
    }

    /**
     * Persist a room and its participants; the unique pair key turns a concurrent
     * double-create into a re-read of the winning row.
     */
    private ChatRoom createRoom(ChatRoom room, String pairKey, UserAccount... participants) {
        try {
            ChatRoom saved = chatRoomRepository.saveAndFlush(room);
            for (UserAccount participant : participants) {
                participantRepository.save(ChatRoomParticipant.of(saved, participant));
            }
            return saved;
        } catch (DataIntegrityViolationException exception) {
            return chatRoomRepository.findByDirectPairKey(pairKey)
                    .orElseThrow(() -> exception);
        }
    }

    /**
     * Every non-admin user gets a "Haru 알림" notice room and a "운영팀" room the
     * first time they open their chat list.
     */
    private void ensureSystemRooms(UserAccount user) {
        if (user.getRoles().contains(Role.ADMIN)) {
            return;
        }
        if (chatRoomRepository.findByDirectPairKey("notice:" + user.getId()).isEmpty()) {
            createRoom(ChatRoom.systemNotice(user.getId()), "notice:" + user.getId(), user);
        }
        if (chatRoomRepository.findByDirectPairKey("ops:" + user.getId()).isEmpty()) {
            List<UserAccount> admins = userAccountRepository.findAllByRole(Role.ADMIN);
            List<UserAccount> participants = new ArrayList<>();
            participants.add(user);
            participants.addAll(admins);
            createRoom(ChatRoom.ops(user.getId()), "ops:" + user.getId(), participants.toArray(UserAccount[]::new));
        }
    }

    private ChatRoomSummary summarize(ChatRoom room, Long viewerId, List<UserAccount> participants) {
        UserAccount counterpart = participants.stream()
                .filter(participant -> !participant.getId().equals(viewerId))
                .findFirst()
                .orElse(null);
        Map<Long, TutorProfile> profiles = counterpart == null
                ? Map.of()
                : tutorProfileRepository.findAllByUserIdIn(Set.of(counterpart.getId())).stream()
                        .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity(), (a, b) -> a));
        ChatRoomParticipant membership = participantRepository.findByChatRoomIdAndUserId(room.getId(), viewerId)
                .orElseThrow(() -> new NotFoundException("Chat room membership was not found."));
        ChatMessage lastMessage = room.getLastMessageId() == null
                ? null
                : messageRepository.findById(room.getLastMessageId()).orElse(null);
        return toSummary(room, membership, counterpart, profiles, lastMessage, viewerId);
    }

    private ChatRoomSummary toSummary(
            ChatRoom room,
            ChatRoomParticipant membership,
            UserAccount counterpart,
            Map<Long, TutorProfile> tutorProfilesByUserId,
            ChatMessage lastMessage,
            Long viewerId
    ) {
        String name;
        String imageUrl = null;
        Long counterpartUserId = counterpart == null ? null : counterpart.getId();

        if (room.getRoomType() == ChatRoomType.SYSTEM_NOTICE) {
            name = SYSTEM_NOTICE_ROOM_NAME;
            counterpartUserId = null;
        } else if (room.getRoomType() == ChatRoomType.OPS && room.getDirectPairKey().equals("ops:" + viewerId)) {
            name = OPS_ROOM_NAME;
        } else if (counterpart != null) {
            TutorProfile profile = tutorProfilesByUserId.get(counterpart.getId());
            if (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isBlank()) {
                name = profile.getDisplayName();
                imageUrl = profile.getThumbnailUrl() != null && !profile.getThumbnailUrl().isBlank()
                        ? profile.getThumbnailUrl()
                        : profile.getProfileImageUrl();
            } else {
                name = counterpart.getName();
            }
        } else {
            name = SYSTEM_NOTICE_ROOM_NAME;
        }

        long unread = countUnread(room.getId(), membership.getLastReadMessageId(), viewerId);
        return new ChatRoomSummary(
                room.getId(),
                room.getRoomType(),
                counterpartUserId,
                name,
                imageUrl,
                preview(lastMessage),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                unread
        );
    }

    private long countUnread(Long roomId, Long lastReadMessageId, Long userId) {
        return messageRepository.countUnread(roomId, lastReadMessageId == null ? 0L : lastReadMessageId, userId);
    }

    private String preview(ChatMessage message) {
        if (message == null) {
            return null;
        }
        if (message.getBody() != null && !message.getBody().isBlank()) {
            return message.getBody();
        }
        if (message.getAttachmentName() != null) {
            return message.getAttachmentName();
        }
        return "[파일]";
    }

    ChatRoomParticipant requireParticipant(Long userId, Long roomId) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new NotFoundException("Chat room was not found.");
        }
        return participantRepository.findByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN, "You do not have access to this chat room."));
    }
}
