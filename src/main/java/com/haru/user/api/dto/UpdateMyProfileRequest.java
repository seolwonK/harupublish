package com.haru.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 30)
        String mobileNumber,

        @NotBlank
        @Size(max = 64)
        String timeZone
) {
}
