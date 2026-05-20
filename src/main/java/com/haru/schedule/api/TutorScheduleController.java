package com.haru.schedule.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.schedule.api.dto.TutorScheduleRequest;
import com.haru.schedule.api.dto.TutorScheduleResponse;
import com.haru.schedule.application.TutorScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@Tag(name = "Schedule", description = "튜터 가능 시간 등록 및 조회 API")
public class TutorScheduleController {

    private final TutorScheduleService tutorScheduleService;

    public TutorScheduleController(TutorScheduleService tutorScheduleService) {
        this.tutorScheduleService = tutorScheduleService;
    }

    @Operation(
            summary = "내 튜터 스케줄 저장",
            description = "로그인한 튜터의 기존 가능 시간 슬롯을 요청 목록으로 교체합니다. 슬롯은 UTC 기준 30분 단위입니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PutMapping("/api/tutors/me/schedule")
    public ApiResponse<TutorScheduleResponse> replaceMySchedule(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody TutorScheduleRequest request
    ) {
        return ApiResponse.success(tutorScheduleService.replaceMySchedule(principal.userId(), request));
    }

    @Operation(
            summary = "내 튜터 스케줄 조회",
            description = "로그인한 튜터의 가능 시간 슬롯을 UTC 범위로 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/api/tutors/me/schedule")
    public ApiResponse<TutorScheduleResponse> getMySchedule(
            @AuthenticationPrincipal HaruPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResponse.success(tutorScheduleService.getMySchedule(principal.userId(), from, to));
    }

    @Operation(
            summary = "공개 튜터 스케줄 조회",
            description = "승인 완료(APPROVED) 튜터의 가능 시간 슬롯만 공개 조회합니다."
    )
    @GetMapping("/api/tutors/{tutorProfileId}/schedule")
    public ApiResponse<TutorScheduleResponse> getPublicSchedule(
            @PathVariable Long tutorProfileId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResponse.success(tutorScheduleService.getPublicSchedule(tutorProfileId, from, to));
    }
}
