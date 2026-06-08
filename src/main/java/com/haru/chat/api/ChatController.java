package com.haru.chat.api;

import com.haru.chat.api.dto.ChatMessageListResponse;
import com.haru.chat.api.dto.ChatMessageResponse;
import com.haru.chat.api.dto.ChatRoomListResponse;
import com.haru.chat.api.dto.ChatRoomSummary;
import com.haru.chat.api.dto.ContactListResponse;
import com.haru.chat.api.dto.MarkReadRequest;
import com.haru.chat.api.dto.SendMessageRequest;
import com.haru.chat.api.dto.StartChatRequest;
import com.haru.chat.api.dto.StartDirectChatRequest;
import com.haru.chat.api.dto.UnreadCountResponse;
import com.haru.chat.application.ChatService;
import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chats")
@Tag(name = "Chat", description = "학생-튜터 1:1 채팅, 시스템 알림 채팅방 API")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(
            summary = "채팅방 생성 또는 조회",
            description = "튜터와의 1:1 채팅방을 생성합니다. 이미 존재하면 기존 방을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping
    public ApiResponse<ChatRoomSummary> startChat(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody StartChatRequest request
    ) {
        return ApiResponse.success(chatService.startDirectRoom(principal.userId(), request));
    }

    @Operation(
            summary = "상대 검색 후 1:1 채팅방 생성/조회",
            description = "검색으로 지정한 사용자(userId)와의 1:1 채팅방을 생성합니다. 이미 존재하면 기존 방을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/direct")
    public ApiResponse<ChatRoomSummary> startDirectChat(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody StartDirectChatRequest request
    ) {
        return ApiResponse.success(chatService.startDirectRoomWithUser(principal.userId(), request.userId()));
    }

    @Operation(
            summary = "채팅 상대 검색",
            description = "이름 또는 이메일로 채팅 가능한 사용자를 검색합니다. (본인/관리자 제외)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/contacts")
    public ApiResponse<ContactListResponse> searchContacts(
            @AuthenticationPrincipal HaruPrincipal principal,
            @RequestParam(required = false) String query
    ) {
        return ApiResponse.success(chatService.searchContacts(principal.userId(), query));
    }

    @Operation(summary = "내 채팅방 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ApiResponse<ChatRoomListResponse> listChats(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(chatService.listMyRooms(principal.userId()));
    }

    @Operation(
            summary = "채팅 메시지 목록 조회",
            description = "오래된 메시지 → 최신 메시지 순으로 반환합니다. beforeId 커서로 이전 페이지를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/{chatRoomId}/messages")
    public ApiResponse<ChatMessageListResponse> getMessages(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(chatService.getMessages(principal.userId(), chatRoomId, beforeId, size));
    }

    @Operation(summary = "채팅 메시지 전송", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{chatRoomId}/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return ApiResponse.success(chatService.sendMessage(principal.userId(), chatRoomId, request));
    }

    @Operation(
            summary = "파일/이미지 첨부 메시지 전송",
            description = "파일을 업로드하고 첨부 메시지를 생성합니다. 5MB 이하의 이미지/문서만 가능합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping(value = "/{chatRoomId}/attachments", consumes = "multipart/form-data")
    public ApiResponse<ChatMessageResponse> sendAttachment(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long chatRoomId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(chatService.sendAttachment(principal.userId(), chatRoomId, file));
    }

    @Operation(
            summary = "메시지 읽음 처리",
            description = "lastMessageId까지 읽음으로 표시합니다. 읽음 포인터는 뒤로 이동하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/{chatRoomId}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody MarkReadRequest request
    ) {
        chatService.markRead(principal.userId(), chatRoomId, request.lastMessageId());
        return ApiResponse.empty();
    }

    @Operation(summary = "전체 미읽음 메시지 수 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(new UnreadCountResponse(chatService.totalUnreadCount(principal.userId())));
    }
}
