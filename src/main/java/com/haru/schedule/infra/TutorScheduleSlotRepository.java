package com.haru.schedule.infra;

import com.haru.schedule.domain.TutorScheduleSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TutorScheduleSlotRepository extends JpaRepository<TutorScheduleSlot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select slot
            from TutorScheduleSlot slot
            where slot.id = :slotId
            """)
    Optional<TutorScheduleSlot> findByIdForUpdate(@Param("slotId") Long slotId);

    List<TutorScheduleSlot> findAllByTutorProfileIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            Long tutorProfileId,
            Instant from,
            Instant to
    );

    @Modifying
    void deleteAllByTutorProfileId(Long tutorProfileId);
}
