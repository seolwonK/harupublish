package com.haru.chat.api.dto;

/** A user that can be messaged, surfaced by contact search. */
public record ContactResponse(
        Long userId,
        String name,
        String imageUrl,
        boolean tutor
) {
}
