package com.haru.auth.api;

import com.haru.auth.api.dto.AuthTokenResponse;
import com.haru.auth.api.dto.LoginRequest;
import com.haru.auth.api.dto.LogoutRequest;
import com.haru.auth.api.dto.RefreshTokenRequest;
import com.haru.auth.api.dto.SignupRequest;
import com.haru.auth.application.AuthService;
import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.user.api.dto.UserMeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "회원가입, 로그인, 로그아웃, access/refresh token 인증 API")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "회원가입",
            description = "일반 사용자 계정을 생성하고 access token, refresh token을 발급합니다. 가입 직후 activeRole은 STUDENT입니다."
    )
    @PostMapping("/signup")
    public ApiResponse<AuthTokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @Operation(
            summary = "로그인",
            description = "이메일 또는 내부 계정 ID와 비밀번호로 로그인합니다. 성공 시 마지막 로그인 시각을 기록하고 새 token pair를 발급합니다."
    )
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(
            summary = "토큰 갱신",
            description = "refresh token을 회전 방식으로 갱신합니다. 사용된 refresh token은 재사용할 수 없습니다."
    )
    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @Operation(
            summary = "로그아웃",
            description = "전달된 refresh token을 폐기합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.empty();
    }

    @Operation(
            summary = "내 인증 정보 조회",
            description = "현재 access token 기준의 사용자 정보, roles, activeRole, tutorProfileStatus를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(authService.me(principal.userId()));
    }
}
