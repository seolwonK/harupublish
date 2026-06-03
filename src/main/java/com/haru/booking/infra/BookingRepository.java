package com.haru.booking.infra;

import com.haru.booking.domain.Booking;
import com.haru.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByScheduleSlotIdAndStatus(Long scheduleSlotId, BookingStatus status);

    @Query("""
            select b.scheduleSlot.id
            from Booking b
            where b.tutorProfile.id = :tutorProfileId
              and b.status = :status
            """)
    List<Long> findScheduleSlotIdsByTutorProfileIdAndStatus(
            @Param("tutorProfileId") Long tutorProfileId,
            @Param("status") BookingStatus status
    );

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user", "scheduleSlot"})
    Optional<Booking> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user", "scheduleSlot"})
    List<Booking> findAllByStudentIdOrderByStartAtAsc(Long studentId);

    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user", "scheduleSlot"})
    List<Booking> findAllByTutorProfileIdOrderByStartAtAsc(Long tutorProfileId);

        @Query("""
            select b.scheduleSlot.id
            from Booking b
            where b.tutorProfile.id = :tutorProfileId
              and b.status = :status
              and b.startAt >= :from
              and b.startAt < :to
            """)
        List<Long> findScheduleSlotIdsByTutorProfileIdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThan(
            @Param("tutorProfileId") Long tutorProfileId,
            @Param("status") BookingStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
        );

    /**
     * Count of lessons a student has consumed for a (tutor, duration) tuple.
     * A lesson is consumed unless it was a NORMAL (in-window) cancel — late
     * cancels and no-shows still burn the credit, so only CANCELLED_NORMAL is
     * excluded. RESERVED bookings with no completion state yet are still
     * consuming the slot, so they count too.
     */
    @Query("""
            select count(b)
            from Booking b
            where b.student.id = :studentId
              and b.tutorProfile.id = :tutorProfileId
              and b.lessonDurationMinutes = :lessonDurationMinutes
              and (b.completionState is null or b.completionState <> com.haru.booking.domain.BookingCompletionState.CANCELLED_NORMAL)
            """)
    long countConsumedLessons(
            @Param("studentId") Long studentId,
            @Param("tutorProfileId") Long tutorProfileId,
            @Param("lessonDurationMinutes") int lessonDurationMinutes
    );

    /**
     * Reserved bookings whose end time has passed and that have not yet been
     * settled. These are the candidates the settlement job finalizes.
     */
    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user", "scheduleSlot"})
    @Query("""
            select b
            from Booking b
            where b.status = com.haru.booking.domain.BookingStatus.RESERVED
              and b.settled = false
              and b.endAt <= :now
            order by b.endAt asc
            """)
    List<Booking> findReservedDueForSettlement(@Param("now") Instant now);

    /**
     * Late-cancel / no-show bookings that have been marked CANCELLED but whose
     * earning has not been credited to the tutor yet.
     */
    @EntityGraph(attributePaths = {"student", "tutorProfile", "tutorProfile.user", "scheduleSlot"})
    @Query("""
            select b
            from Booking b
            where b.settled = false
              and b.completionState = com.haru.booking.domain.BookingCompletionState.CANCELLED_LATE
            order by b.updatedAt asc
            """)
    List<Booking> findLateCancelDueForSettlement();
}
