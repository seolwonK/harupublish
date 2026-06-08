package com.haru.chat.config;

import com.haru.common.security.HaruPrincipal;

import java.security.Principal;

/**
 * Session principal for STOMP connections. {@link #getName()} returns the user
 * id so {@code convertAndSendToUser(userId, ...)} resolves user destinations.
 */
public record StompPrincipal(HaruPrincipal haruPrincipal) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(haruPrincipal.userId());
    }

    public Long userId() {
        return haruPrincipal.userId();
    }
}
