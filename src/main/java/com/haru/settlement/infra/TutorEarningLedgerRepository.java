package com.haru.settlement.infra;

import com.haru.settlement.domain.TutorEarningLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TutorEarningLedgerRepository extends JpaRepository<TutorEarningLedger, Long> {

    /**
     * Latest ledger row for a tutor under a pessimistic write lock so concurrent
     * earning writes serialize on the running balance. Returns empty for a tutor
     * with no entries yet (balance = 0).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entry
            from TutorEarningLedger entry
            where entry.tutorProfileId = :tutorProfileId
            order by entry.id desc
            """)
    List<TutorEarningLedger> findLatestForUpdate(@Param("tutorProfileId") Long tutorProfileId, Limit limit);

    default Optional<TutorEarningLedger> findLatestForUpdate(Long tutorProfileId) {
        return findLatestForUpdate(tutorProfileId, Limit.of(1)).stream().findFirst();
    }

    Optional<TutorEarningLedger> findFirstByBookingId(Long bookingId);

    Optional<TutorEarningLedger> findFirstByBookingIdAndEntryType(Long bookingId, com.haru.settlement.domain.EarningEntryType entryType);

    List<TutorEarningLedger> findAllByTutorProfileIdOrderByIdDesc(Long tutorProfileId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
