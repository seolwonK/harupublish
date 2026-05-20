package com.haru.tutor.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.tutor.api.dto.ExpertListResponse;
import com.haru.tutor.api.dto.ImageUploadResponse;
import com.haru.tutor.api.dto.TutorProfileRequest;
import com.haru.tutor.api.dto.TutorProfileResponse;
import com.haru.tutor.application.ImageStorageService;
import com.haru.tutor.application.TutorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@Tag(name = "Tutor", description = "튜터 전환, 튜터 프로필 작성, 관리자 승인, Experts 공개 목록 API")
public class TutorController {

    private final TutorService tutorService;
    private final ImageStorageService imageStorageService;

    public TutorController(TutorService tutorService, ImageStorageService imageStorageService) {
        this.tutorService = tutorService;
        this.imageStorageService = imageStorageService;
    }

    @Operation(
            summary = "튜터 모드 전환",
            description = "로그인 사용자의 roles에 TUTOR를 추가하고 activeRole을 TUTOR로 변경합니다. tutor_profile이 없으면 DRAFT 상태로 생성합니다. 이 단계는 승인 완료가 아니며 Experts 목록에 노출되지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/api/tutors/me/switch")
    public ApiResponse<TutorProfileResponse> switchToTutor(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(tutorService.switchToTutor(principal.userId()));
    }

    @Operation(
            summary = "내 튜터 프로필 조회",
            description = "로그인 사용자의 튜터 프로필과 DRAFT/PENDING/APPROVED/REJECTED 상태를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/api/tutors/me/profile")
    public ApiResponse<TutorProfileResponse> getMyProfile(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(tutorService.getMyProfile(principal.userId()));
    }

    @Operation(
            summary = "내 튜터 프로필 임시저장",
            description = "튜터 소개, 언어, 가격, 가능 시간, 지급수단 등을 저장합니다. 저장만으로 승인되지는 않습니다. APPROVED 또는 REJECTED 상태에서 수정하면 DRAFT로 돌아갑니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PutMapping("/api/tutors/me/profile")
    public ApiResponse<TutorProfileResponse> updateMyProfile(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody TutorProfileRequest request
    ) {
        return ApiResponse.success(tutorService.updateMyProfile(principal.userId(), request));
    }

    @Operation(
            summary = "튜터 프로필 이미지 업로드",
            description = "프로필 이미지 또는 썸네일로 사용할 이미지 파일을 업로드하고, 프로필 저장 요청에 넣을 수 있는 URL을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping(value = "/api/tutors/me/profile/images", consumes = "multipart/form-data")
    public ApiResponse<ImageUploadResponse> uploadProfileImage(
            @AuthenticationPrincipal HaruPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        String url = imageStorageService.storeTutorProfileImage(principal.userId(), file);
        return ApiResponse.success(new ImageUploadResponse(url));
    }

    @Operation(
            summary = "튜터 프로필 승인요청 제출",
            description = "필수값이 채워진 튜터 프로필을 PENDING 상태로 변경합니다. 관리자가 승인하기 전까지 Experts 목록에 노출되지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/api/tutors/me/profile/submit")
    public ApiResponse<TutorProfileResponse> submitMyProfile(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(tutorService.submitMyProfile(principal.userId()));
    }

    @Operation(
            summary = "튜터 프로필 승인",
            description = "관리자가 PENDING 상태의 튜터 프로필을 APPROVED로 변경합니다. 승인 후 고객용 Experts 목록에 노출됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PatchMapping("/api/admin/tutors/{tutorProfileId}/approve")
    public ApiResponse<TutorProfileResponse> approve(@PathVariable Long tutorProfileId) {
        return ApiResponse.success(tutorService.approve(tutorProfileId));
    }

    @Operation(
            summary = "튜터 프로필 반려",
            description = "관리자가 PENDING 상태의 튜터 프로필을 REJECTED로 변경합니다. 반려된 프로필은 Experts 목록에 노출되지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PatchMapping("/api/admin/tutors/{tutorProfileId}/reject")
    public ApiResponse<TutorProfileResponse> reject(@PathVariable Long tutorProfileId) {
        return ApiResponse.success(tutorService.reject(tutorProfileId));
    }

    @Operation(
            summary = "튜터 승인 대기 목록 조회",
            description = "관리자가 PENDING 상태의 튜터 프로필을 신청 시각 오름차순으로 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/api/admin/tutors/pending")
    public ApiResponse<List<TutorProfileResponse>> getPendingProfiles() {
        return ApiResponse.success(tutorService.getPendingProfiles());
    }

    @Operation(
            summary = "Experts 공개 목록 조회",
            description = "고객 메인에 노출할 승인 완료(APPROVED) 튜터 프로필만 조회합니다. 인증 없이 호출할 수 있습니다."
    )
    @GetMapping("/api/tutors")
    public ApiResponse<List<ExpertListResponse>> getApprovedExperts() {
        return ApiResponse.success(tutorService.getApprovedExperts());
    }

    @Operation(
            summary = "Expert 공개 상세 조회",
            description = "승인 완료(APPROVED) 튜터 프로필 상세만 공개 조회합니다. 미승인 프로필은 공개 API에서 찾을 수 없습니다."
    )
    @GetMapping("/api/tutors/{tutorProfileId}")
    public ApiResponse<TutorProfileResponse> getApprovedExpert(@PathVariable Long tutorProfileId) {
        return ApiResponse.success(tutorService.getApprovedProfile(tutorProfileId));
    }
}
