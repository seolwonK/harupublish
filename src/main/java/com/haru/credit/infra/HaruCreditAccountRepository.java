package com.haru.credit.infra;

import com.haru.credit.domain.HaruCreditAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HaruCreditAccountRepository extends JpaRepository<HaruCreditAccount, Long> {

    Optional<HaruCreditAccount> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from HaruCreditAccount account where account.userId = :userId")
    Optional<HaruCreditAccount> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("""
            select account
            from HaruCreditAccount account
            where account.balanceUsd > 0
              and account.expiresAt <= :now
            """)
    List<HaruCreditAccount> findExpiredAccounts(@Param("now") Instant now);
}
