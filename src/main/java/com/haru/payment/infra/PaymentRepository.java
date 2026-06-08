package com.haru.payment.infra;

import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user"})
    Optional<Payment> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user"})
    List<Payment> findAllByStudentIdOrderByCreatedAtDesc(Long studentId);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user"})
    List<Payment> findAllByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, PaymentStatus status);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user"})
    List<Payment> findAllByStatusOrderByCreatedAtAsc(PaymentStatus status);

    boolean existsByProviderOrderIdAndIdNot(String providerOrderId, Long id);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user"})
    List<Payment> findAllByStudentIdAndTutorProfileIdAndLessonDurationMinutesAndStatusOrderByCreatedAtAsc(
            Long studentId,
            Long tutorProfileId,
            int lessonDurationMinutes,
            PaymentStatus status
    );

    @Query("""
            select coalesce(sum(payment.lessonPackCount), 0)
            from Payment payment
            where payment.student.id = :studentId
              and payment.tutorProfile.id = :tutorProfileId
              and payment.lessonDurationMinutes = :lessonDurationMinutes
              and payment.status = :status
            """)
    Long sumLessonPackCount(
            @Param("studentId") Long studentId,
            @Param("tutorProfileId") Long tutorProfileId,
            @Param("lessonDurationMinutes") int lessonDurationMinutes,
            @Param("status") PaymentStatus status
    );
}
