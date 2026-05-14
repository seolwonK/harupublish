package com.haru.common.security;

import com.haru.user.domain.Role;

import java.util.Set;

public record HaruPrincipal(
        Long userId,
        String email,
        Set<Role> roles,
        Role activeRole
) {
}
