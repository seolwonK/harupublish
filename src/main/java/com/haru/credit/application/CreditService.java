package com.haru.credit.application;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.credit.domain.HaruCreditAccount;
import com.haru.credit.domain.HaruCreditLedger;
import com.haru.credit.infra.HaruCreditAccountRepository;
import com.haru.credit.infra.HaruCreditLedgerRepository;
import com.haru.settings.application.PlatformSettingsService;
import com.haru.settings.domain.FeePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Haru credit (refund-only virtual currency, USD) operations.
 *
 * <p>Credit is issued on an approved refund ({@code REFUND_ISSUED}, +), spent on
 * future packs ({@code SPENT}, -) FIFO by issuance order, and expires whole
 * ({@code EXPIRED}, -) after 12 months of account inactivity. The account row is
 * the running balance; the ledger is append-only. Refund issuance is idempotent
 * on an {@code idempotencyKey} (unique in the DB) so a refund cannot be
 * double-credited even under retries or a provider/admin race.</p>
 */
@Service
public class CreditService {

    private final HaruCreditAccountRepository creditAccountRepository;
    private final HaruCreditLedgerRepository creditLedgerRepository;
    private final PlatformSettingsService platformSettingsService;

    public CreditService(
            HaruCreditAccountRepository creditAccountRepository,
            HaruCreditLedgerRepository creditLedgerRepository,
            PlatformSettingsService platformSettingsService
    ) {
        this.creditAccountRepository = creditAccountRepository;
        this.creditLedgerRepository = creditLedgerRepository;
        this.platformSettingsService = platformSettingsService;
    }

    /**
     * Issue refund credit to a user. Idempotent on {@code idempotencyKey}: if a
     * ledger row already exists for the key, the existing balance is returned and
     * nothing new is written. Returns the resulting account balance.
     */
    @Transactional
    public BigDecimal issueRefund(Long userId, BigDecimal amountUsd, Long paymentId, String idempotencyKey, String memo, Instant now) {
        if (amountUsd == null || amountUsd.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Refund credit amount must be positive.");
        }
        if (idempotencyKey != null && creditLedgerRepository.existsByIdempotencyKey(idempotencyKey)) {
            // Already issued for this key — return the current balance, no double credit.
            return currentBalance(userId);
        }

        FeePolicy feePolicy = platformSettingsService.currentFeePolicy();
        int expiryMonths = feePolicy.creditExpiryMonths();

        HaruCreditAccount account = creditAccountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> creditAccountRepository.save(HaruCreditAccount.open(userId, expiryMonths, now)));
        account.issue(amountUsd, expiryMonths, now);

        creditLedgerRepository.save(HaruCreditLedger.refundIssued(
                account.getId(),
                amountUsd,
                account.getBalanceUsd(),
                paymentId,
                idempotencyKey,
                memo,
                now
        ));
        return account.getBalanceUsd();
    }

    /**
     * Spend credit FIFO (oldest issuance first). The account holds the running
     * balance; "FIFO" is the ledger's natural insertion order. Throws when the
     * balance is insufficient.
     */
    @Transactional
    public BigDecimal spend(Long userId, BigDecimal amountUsd, Long paymentId, String idempotencyKey, String memo, Instant now) {
        if (idempotencyKey != null && creditLedgerRepository.existsByIdempotencyKey(idempotencyKey)) {
            return currentBalance(userId);
        }
        FeePolicy feePolicy = platformSettingsService.currentFeePolicy();
        int expiryMonths = feePolicy.creditExpiryMonths();

        HaruCreditAccount account = creditAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "No Haru credit account to spend from."));
        account.spend(amountUsd, expiryMonths, now);

        creditLedgerRepository.save(HaruCreditLedger.spent(
                account.getId(),
                amountUsd,
                account.getBalanceUsd(),
                paymentId,
                idempotencyKey,
                memo,
                now
        ));
        return account.getBalanceUsd();
    }

    /**
     * Expire every account whose balance is positive and whose account-level
     * expiry has passed. One ledger EXPIRED row per account; returns the count
     * expired.
     */
    @Transactional
    public int expireDueAccounts(Instant now) {
        List<HaruCreditAccount> due = creditAccountRepository.findExpiredAccounts(now);
        int expired = 0;
        for (HaruCreditAccount account : due) {
            BigDecimal amount = account.expireAll(now);
            if (amount.signum() > 0) {
                creditLedgerRepository.save(HaruCreditLedger.expired(
                        account.getId(),
                        amount,
                        account.getBalanceUsd(),
                        "Credit expired after inactivity (account #" + account.getId() + ")",
                        now
                ));
                expired++;
            }
        }
        return expired;
    }

    @Transactional(readOnly = true)
    public BigDecimal currentBalance(Long userId) {
        return creditAccountRepository.findByUserId(userId)
                .map(HaruCreditAccount::getBalanceUsd)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    @Transactional(readOnly = true)
    public Optional<HaruCreditAccount> findAccount(Long userId) {
        return creditAccountRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<HaruCreditLedger> ledgerFor(Long accountId) {
        return creditLedgerRepository.findAllByAccountIdOrderByIdDesc(accountId);
    }
}
