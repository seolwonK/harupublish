package com.haru.dev.api;

import com.haru.common.exception.NotFoundException;
import com.haru.common.response.ApiResponse;
import com.haru.dev.api.dto.AddSlotsRequest;
import com.haru.dev.api.dto.CreateTestBookingRequest;
import com.haru.dev.api.dto.CreateTestStudentRequest;
import com.haru.dev.api.dto.CreateTestTutorRequest;
import com.haru.dev.api.dto.CreateTestTutorResponse;
import com.haru.dev.api.dto.CreatedLessonResponse;
import com.haru.dev.api.dto.CreatedSlotResponse;
import com.haru.dev.api.dto.DevStatusResponse;
import com.haru.dev.api.dto.TestAccountResponse;
import com.haru.dev.application.DevService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Developer-only test-data endpoints. The whole controller is inert unless
 * {@code haru.dev.enabled=true}; when disabled every route answers 404 so its
 * existence is not advertised. Intended for local and non-production test-MVP
 * deployments — never enable it on a real production environment.
 */
@RestController
@RequestMapping("/api/dev")
@Tag(name = "Dev", description = "개발자용 테스트 데이터 생성 API (haru.dev.enabled=true 일 때만 동작)")
public class DevController {

    private final DevService devService;
    private final boolean enabled;

    public DevController(DevService devService, @Value("${haru.dev.enabled:false}") boolean enabled) {
        this.devService = devService;
        this.enabled = enabled;
    }

    @Operation(summary = "dev 도구 활성 여부", description = "활성화된 경우에만 200을 반환합니다. 비활성이면 404.")
    @GetMapping("/status")
    public ApiResponse<DevStatusResponse> status() {
        ensureEnabled();
        return ApiResponse.success(new DevStatusResponse(true));
    }

    @Operation(summary = "테스트 튜터 생성", description = "승인 완료된 튜터를 만들고 요청 시 공개 슬롯/예약 수업까지 함께 생성합니다.")
    @PostMapping("/tutors")
    public ApiResponse<CreateTestTutorResponse> createTutor(@RequestBody(required = false) CreateTestTutorRequest request) {
        ensureEnabled();
        return ApiResponse.success(devService.createTutor(orEmptyTutor(request)));
    }

    @Operation(summary = "테스트 학생 생성", description = "학생 계정을 생성하고 로그인용 자격증명을 반환합니다.")
    @PostMapping("/students")
    public ApiResponse<TestAccountResponse> createStudent(@RequestBody(required = false) CreateTestStudentRequest request) {
        ensureEnabled();
        return ApiResponse.success(devService.createStudent(orEmptyStudent(request)));
    }

    @Operation(summary = "공개 수업 슬롯 추가", description = "튜터에게 예약 가능한 30분 슬롯을 날짜/시간 지정으로 추가합니다(기존 일정 유지).")
    @PostMapping("/tutors/{tutorProfileId}/slots")
    public ApiResponse<List<CreatedSlotResponse>> addSlots(
            @PathVariable Long tutorProfileId,
            @RequestBody AddSlotsRequest request
    ) {
        ensureEnabled();
        return ApiResponse.success(devService.addSlots(tutorProfileId, request));
    }

    @Operation(summary = "예약 수업 생성", description = "결제 없이 PAID 처리하여 예약된 수업 1건을 생성합니다(학생 미지정 시 자동 생성).")
    @PostMapping("/bookings")
    public ApiResponse<CreatedLessonResponse> createBooking(@RequestBody CreateTestBookingRequest request) {
        ensureEnabled();
        return ApiResponse.success(devService.createBooking(request));
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new NotFoundException("Not found.");
        }
    }

    private CreateTestTutorRequest orEmptyTutor(CreateTestTutorRequest request) {
        return request != null
                ? request
                : new CreateTestTutorRequest(null, null, null, null, null, null, null, null, null, null);
    }

    private CreateTestStudentRequest orEmptyStudent(CreateTestStudentRequest request) {
        return request != null ? request : new CreateTestStudentRequest(null, null, null);
    }
}
