package com.haru.schedule.infra;

import com.haru.schedule.domain.TutorScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.List;

public interface TutorScheduleSlotRepository extends JpaRepository<TutorScheduleSlot, Long> {

    List<TutorScheduleSlot> findAllByTutorProfileIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            Long tutorProfileId,
            Instant from,
            Instant to
    );

    @Modifying
    void deleteAllByTutorProfileId(Long tutorProfileId);
}
