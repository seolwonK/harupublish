package com.haru.credit.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Daily batch that expires Haru credit accounts inactive for longer than the
 * configured window (default 12 months). Expiry is account-level (single rule),
 * idempotent (only positive balances past their {@code expiresAt} are touched),
 * and safely re-runnable.
 */
@Component
public class CreditExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(CreditExpiryJob.class);

    private final CreditService creditService;

    public CreditExpiryJob(CreditService creditService) {
        this.creditService = creditService;
    }

    /** Runs daily (after a 5-minute startup delay). */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "P1D")
    public void expireDueCredits() {
        int expired = creditService.expireDueAccounts(Instant.now());
        if (expired > 0) {
            log.info("Credit expiry job expired {} Haru credit account(s).", expired);
        }
    }
}
