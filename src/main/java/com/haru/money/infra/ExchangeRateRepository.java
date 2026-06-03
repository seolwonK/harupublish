package com.haru.money.infra;

import com.haru.money.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByCapturedAtDesc(
            String baseCurrency,
            String quoteCurrency
    );
}
