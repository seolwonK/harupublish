package com.haru.user.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public enum Role {
    STUDENT,
    TUTOR,
    ADMIN;

    public GrantedAuthority authority() {
        return new SimpleGrantedAuthority("ROLE_" + name());
    }
}
