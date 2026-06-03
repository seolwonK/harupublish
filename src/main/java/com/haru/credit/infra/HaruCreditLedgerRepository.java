package com.haru.credit.infra;

import com.haru.credit.domain.HaruCreditLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HaruCreditLedgerRepository extends JpaRepository<HaruCreditLedger, Long> {

    List<HaruCreditLedger> findAllByAccountIdOrderByIdDesc(Long accountId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<HaruCreditLedger> findFirstByIdempotencyKey(String idempotencyKey);
}
