package com.haru.review.infra;

import com.haru.review.domain.Review;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    @EntityGraph(attributePaths = {"student", "booking"})
    List<Review> findAllByTutorProfileIdAndVisibleTrueOrderByCreatedAtDesc(Long tutorProfileId);

    int countByTutorProfileIdAndVisibleTrue(Long tutorProfileId);

    @Query("select avg(review.rating) from Review review where review.tutorProfile.id = :tutorProfileId and review.visible = true")
    Double averageRatingByTutorProfileId(@Param("tutorProfileId") Long tutorProfileId);
}
