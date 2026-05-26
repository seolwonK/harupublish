package com.haru.review.api.dto;

import com.haru.review.domain.Review;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        Long bookingId,
        Long tutorProfileId,
        Long studentUserId,
        String reviewerName,
        Integer rating,
        String body,
        Instant createdAt
) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBooking() == null ? null : review.getBooking().getId(),
                review.getTutorProfile().getId(),
                review.getStudent() == null ? null : review.getStudent().getId(),
                review.getReviewerName(),
                review.getRating(),
                review.getBody(),
                review.getCreatedAt()
        );
    }
}
