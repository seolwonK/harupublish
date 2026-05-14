package com.haru.user.api.dto;

import com.haru.user.domain.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeActiveRoleRequest(
        @NotNull
        Role activeRole
) {
}
