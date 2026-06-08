package com.haru.chat.api.dto;

import java.util.List;

public record ContactListResponse(
        List<ContactResponse> contacts
) {
}
