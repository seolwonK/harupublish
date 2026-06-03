package com.haru.settlement.infra;

import com.haru.settlement.domain.Withdrawal;
import com.haru.settlement.domain.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    List<Withdrawal> findAllByTutorProfileIdOrderByCreatedAtDesc(Long tutorProfileId);

    List<Withdrawal> findAllByOrderByCreatedAtDesc();

    List<Withdrawal> findAllByStatusOrderByCreatedAtDesc(WithdrawalStatus status);
}
