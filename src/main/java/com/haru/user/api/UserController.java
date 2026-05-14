package com.haru.user.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.user.api.dto.ChangeActiveRoleRequest;
import com.haru.user.api.dto.UpdateMyProfileRequest;
import com.haru.user.api.dto.UserMeResponse;
import com.haru.user.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "User", description = "내 사용자 프로필과 현재 사용 모드(activeRole) 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "내 사용자 프로필 조회",
            description = "로그인한 사용자의 기본 정보, 보유 roles, activeRole, tutorProfileStatus를 조회합니다."
    )
    @GetMapping
    public ApiResponse<UserMeResponse> getMe(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(userService.getMe(principal.userId()));
    }

    @Operation(
            summary = "내 사용자 프로필 수정",
            description = "이름, 휴대폰 번호, 타임존을 수정합니다. timeZone은 IANA timezone 형식이어야 합니다."
    )
    @PatchMapping
    public ApiResponse<UserMeResponse> updateMe(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return ApiResponse.success(userService.updateMe(principal.userId(), request));
    }

    @Operation(
            summary = "현재 사용 모드 변경",
            description = "activeRole을 STUDENT, TUTOR, ADMIN 중 계정에 이미 부여된 role로 변경합니다. TUTOR role은 튜터 전환 API 이후 사용할 수 있습니다."
    )
    @PatchMapping("/active-role")
    public ApiResponse<UserMeResponse> changeActiveRole(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody ChangeActiveRoleRequest request
    ) {
        return ApiResponse.success(userService.changeActiveRole(principal.userId(), request));
    }
}
