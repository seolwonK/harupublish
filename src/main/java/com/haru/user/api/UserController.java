package com.haru.user.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.user.api.dto.ChangeActiveRoleRequest;
import com.haru.user.api.dto.UpdateMyProfileRequest;
import com.haru.user.api.dto.UserMeResponse;
import com.haru.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<UserMeResponse> getMe(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(userService.getMe(principal.userId()));
    }

    @PatchMapping
    public ApiResponse<UserMeResponse> updateMe(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return ApiResponse.success(userService.updateMe(principal.userId(), request));
    }

    @PatchMapping("/active-role")
    public ApiResponse<UserMeResponse> changeActiveRole(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody ChangeActiveRoleRequest request
    ) {
        return ApiResponse.success(userService.changeActiveRole(principal.userId(), request));
    }
}
