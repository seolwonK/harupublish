package com.haru.chat.api.dto;

import java.util.List;

public record ChatRoomListResponse(
        List<ChatRoomSummary> rooms
) {
}
