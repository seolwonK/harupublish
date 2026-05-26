package com.haru.review.application;

import com.haru.booking.domain.Booking;
import com.haru.booking.domain.BookingStatus;
import com.haru.booking.infra.BookingRepository;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.NotFoundException;
import com.haru.review.api.dto.CreateReviewRequest;
import com.haru.review.api.dto.ReviewListResponse;
import com.haru.review.api.dto.ReviewResponse;
import com.haru.review.domain.Review;
import com.haru.review.infra.ReviewRepository;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.tutor.infra.TutorProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ReviewService {

    private final BookingRepository bookingRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final ReviewRepository reviewRepository;

    public ReviewService(
            BookingRepository bookingRepository,
            TutorProfileRepository tutorProfileRepository,
            ReviewRepository reviewRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.tutorProfileRepository = tutorProfileRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public ReviewResponse create(Long userId, Long bookingId, CreateReviewRequest request) {
        Booking booking = bookingRepository.findWithDetailsById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking was not found."));

        if (!booking.getStudent().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the student can review this booking.");
        }
        if (booking.effectiveStatus(Instant.now()) != BookingStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only completed lessons can be reviewed.");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Review already exists for this booking.");
        }

        Review review = reviewRepository.save(Review.fromBooking(booking, request.rating(), request.body()));
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public ReviewListResponse getPublicTutorReviews(Long tutorProfileId) {
        tutorProfileRepository.findByIdAndStatusAndHiddenFalse(tutorProfileId, TutorProfileStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
        List<Review> reviews = reviewRepository.findAllByTutorProfileIdAndVisibleTrueOrderByCreatedAtDesc(tutorProfileId);
        return ReviewListResponse.from(tutorProfileId, reviews);
    }
}
