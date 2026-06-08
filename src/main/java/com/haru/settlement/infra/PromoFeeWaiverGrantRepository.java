package com.haru.settlement.infra;

import com.haru.settlement.domain.PromoFeeWaiverGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromoFeeWaiverGrantRepository extends JpaRepository<PromoFeeWaiverGrant, Long> {

    Optional<PromoFeeWaiverGrant> findByTutorProfileId(Long tutorProfileId);

    long countByActiveTrue();

    List<PromoFeeWaiverGrant> findAllByActiveTrue();
}
