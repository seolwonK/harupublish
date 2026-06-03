package com.haru.settlement.infra;

import com.haru.settlement.domain.MonthlySettlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonthlySettlementRepository extends JpaRepository<MonthlySettlement, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select settlement
            from MonthlySettlement settlement
            where settlement.tutorProfileId = :tutorProfileId
              and settlement.settlementYear = :year
              and settlement.settlementMonth = :month
            """)
    Optional<MonthlySettlement> findForUpdate(
            @Param("tutorProfileId") Long tutorProfileId,
            @Param("year") int year,
            @Param("month") int month
    );

    Optional<MonthlySettlement> findByTutorProfileIdAndSettlementYearAndSettlementMonth(
            Long tutorProfileId,
            int settlementYear,
            int settlementMonth
    );

    List<MonthlySettlement> findAllByTutorProfileIdOrderBySettlementYearDescSettlementMonthDesc(Long tutorProfileId);
}
