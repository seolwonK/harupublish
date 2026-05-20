package com.haru.booking.infra;

import com.haru.booking.domain.Booking;
import com.haru.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByScheduleSlotIdAndStatus(Long scheduleSlotId, BookingStatus status);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user", "scheduleSlot"})
    Optional<Booking> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user", "scheduleSlot"})
    List<Booking> findAllByStudentIdOrderByStartAtAsc(Long studentId);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user", "scheduleSlot"})
    List<Booking> findAllByTutorProfileIdOrderByStartAtAsc(Long tutorProfileId);
}
