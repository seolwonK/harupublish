package com.haru.user.api.dto;

import com.haru.user.domain.AccountStatus;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;

import java.time.Instant;
import java.util.Set;

public record UserMeResponse(
        Long id,
        String email,
        String name,
        String mobileNumber,
        Set<Role> roles,
        Role activeRole,
        AccountStatus accountStatus,
        String timeZone,
        Instant lastLoginAt,
        TutorProfileStatus tutorProfileStatus
) {

    public static UserMeResponse from(UserAccount user) {
        return from(user, null);
    }

    public static UserMeResponse from(UserAccount user, TutorProfileStatus tutorProfileStatus) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getMobileNumber(),
                user.getRoles(),
                user.getActiveRole(),
                user.getAccountStatus(),
                user.getTimeZone(),
                user.getLastLoginAt(),
                tutorProfileStatus
        );
    }
}
