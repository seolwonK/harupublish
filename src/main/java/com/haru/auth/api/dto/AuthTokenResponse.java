package com.haru.auth.api.dto;

import com.haru.user.api.dto.UserMeResponse;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        UserMeResponse user
) {
}
