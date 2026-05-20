package com.haru.booking.api;

import com.haru.booking.api.dto.BookingJoinResponse;
import com.haru.booking.api.dto.BookingListResponse;
import com.haru.booking.api.dto.BookingResponse;
import com.haru.booking.api.dto.CancelBookingRequest;
import com.haru.booking.api.dto.CreateBookingRequest;
import com.haru.booking.application.BookingService;
import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Booking", description = "25분 수업 예약, 조회, 취소, 입장 가능 여부 API")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(
            summary = "예약 생성",
            description = "승인된 튜터의 스케줄 슬롯으로 25분 수업 예약을 생성합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping
    public ApiResponse<BookingResponse> create(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        return ApiResponse.success(bookingService.create(principal.userId(), request));
    }

    @Operation(summary = "내 예약 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ApiResponse<BookingListResponse> getMyBookings(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(bookingService.getMyBookings(principal.userId()));
    }

    @Operation(summary = "예약 상세 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> getBooking(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long bookingId
    ) {
        return ApiResponse.success(bookingService.getBooking(principal.userId(), bookingId));
    }

    @Operation(summary = "예약 취소", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{bookingId}/cancel")
    public ApiResponse<BookingResponse> cancel(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long bookingId,
            @Valid @RequestBody CancelBookingRequest request
    ) {
        return ApiResponse.success(bookingService.cancel(principal.userId(), bookingId, request));
    }

    @Operation(summary = "수업 입장 가능 여부 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{bookingId}/join")
    public ApiResponse<BookingJoinResponse> join(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long bookingId
    ) {
        return ApiResponse.success(bookingService.join(principal.userId(), bookingId));
    }
}
