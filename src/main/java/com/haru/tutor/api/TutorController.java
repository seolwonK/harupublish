package com.haru.tutor.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.tutor.api.dto.ExpertListResponse;
import com.haru.tutor.api.dto.TutorProfileRequest;
import com.haru.tutor.api.dto.TutorProfileResponse;
import com.haru.tutor.application.TutorService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TutorController {

    private final TutorService tutorService;

    public TutorController(TutorService tutorService) {
        this.tutorService = tutorService;
    }

    @PostMapping("/api/tutors/me/switch")
    public ApiResponse<TutorProfileResponse> switchToTutor(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(tutorService.switchToTutor(principal.userId()));
    }

    @GetMapping("/api/tutors/me/profile")
    public ApiResponse<TutorProfileResponse> getMyProfile(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(tutorService.getMyProfile(principal.userId()));
    }

    @PutMapping("/api/tutors/me/profile")
    public ApiResponse<TutorProfileResponse> updateMyProfile(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody TutorProfileRequest request
    ) {
        return ApiResponse.success(tutorService.updateMyProfile(principal.userId(), request));
    }

    @PostMapping("/api/tutors/me/profile/submit")
    public ApiResponse<TutorProfileResponse> submitMyProfile(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(tutorService.submitMyProfile(principal.userId()));
    }

    @PatchMapping("/api/admin/tutors/{tutorProfileId}/approve")
    public ApiResponse<TutorProfileResponse> approve(@PathVariable Long tutorProfileId) {
        return ApiResponse.success(tutorService.approve(tutorProfileId));
    }

    @PatchMapping("/api/admin/tutors/{tutorProfileId}/reject")
    public ApiResponse<TutorProfileResponse> reject(@PathVariable Long tutorProfileId) {
        return ApiResponse.success(tutorService.reject(tutorProfileId));
    }

    @GetMapping("/api/tutors")
    public ApiResponse<List<ExpertListResponse>> getApprovedExperts() {
        return ApiResponse.success(tutorService.getApprovedExperts());
    }
}
