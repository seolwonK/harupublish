package com.haru.review.api.dto;

import com.haru.review.domain.Review;

import java.util.List;

public record ReviewListResponse(
        Long tutorProfileId,
        Double averageRating,
        Integer reviewCount,
        List<ReviewResponse> reviews
) {

    public static ReviewListResponse from(Long tutorProfileId, List<Review> reviews) {
        double average = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0);
        return new ReviewListResponse(
                tutorProfileId,
                Math.round(average * 10.0) / 10.0,
                reviews.size(),
                reviews.stream().map(ReviewResponse::from).toList()
        );
    }
}
