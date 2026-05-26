package com.haru.review.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.review.api.dto.CreateReviewRequest;
import com.haru.review.api.dto.ReviewListResponse;
import com.haru.review.api.dto.ReviewResponse;
import com.haru.review.application.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Review", description = "Lesson reviews and public tutor review summaries")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Write a review for a completed booking", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/bookings/{bookingId}/reviews")
    public ApiResponse<ReviewResponse> create(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long bookingId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return ApiResponse.success(reviewService.create(principal.userId(), bookingId, request));
    }

    @Operation(summary = "Get public tutor reviews")
    @GetMapping("/api/tutors/{tutorProfileId}/reviews")
    public ApiResponse<ReviewListResponse> getPublicReviews(@PathVariable Long tutorProfileId) {
        return ApiResponse.success(reviewService.getPublicTutorReviews(tutorProfileId));
    }
}
