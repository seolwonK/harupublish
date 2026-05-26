package com.haru.review.domain;

import com.haru.booking.domain.Booking;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.tutor.domain.TutorProfile;
import com.haru.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_profile_id", nullable = false)
    private TutorProfile tutorProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_user_id")
    private UserAccount student;

    @Column(name = "reviewer_name", nullable = false, length = 100)
    private String reviewerName;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private boolean visible;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() {
    }

    private Review(Booking booking, TutorProfile tutorProfile, UserAccount student, String reviewerName, int rating, String body) {
        validate(rating, body);
        this.booking = booking;
        this.tutorProfile = tutorProfile;
        this.student = student;
        this.reviewerName = reviewerName;
        this.rating = rating;
        this.body = body.trim();
        this.visible = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Review fromBooking(Booking booking, int rating, String body) {
        return new Review(
                booking,
                booking.getTutorProfile(),
                booking.getStudent(),
                booking.getStudent().getName(),
                rating,
                body
        );
    }

    public static Review seed(TutorProfile tutorProfile, String reviewerName, int rating, String body) {
        return new Review(null, tutorProfile, null, reviewerName, rating, body);
    }

    private static void validate(int rating, String body) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "rating must be between 1 and 5.");
        }
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "review body is required.");
        }
        if (body.length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "review body must be 1000 characters or less.");
        }
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public TutorProfile getTutorProfile() {
        return tutorProfile;
    }

    public UserAccount getStudent() {
        return student;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public int getRating() {
        return rating;
    }

    public String getBody() {
        return body;
    }

    public boolean isVisible() {
        return visible;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
