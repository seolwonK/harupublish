package com.haru.payment.infra;

import com.haru.payment.domain.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user"})
    Optional<Payment> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user"})
    List<Payment> findAllByStudentIdOrderByCreatedAtDesc(Long studentId);
}
