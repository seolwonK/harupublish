package com.haru.settlement.application;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.NotFoundException;
import com.haru.settings.application.PlatformSettingsService;
import com.haru.settings.domain.FeePolicy;
import com.haru.settlement.domain.PromoFeeWaiverGrant;
import com.haru.settlement.domain.Withdrawal;
import com.haru.settlement.domain.WithdrawalMethod;
import com.haru.settlement.domain.WithdrawalStatus;
import com.haru.settlement.infra.PromoFeeWaiverGrantRepository;
import com.haru.settlement.infra.WithdrawalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Tutor cash-out workflow over the USD "cyber money" balance.
 *
 * <p>On request the gross amount is held (debited) on the earning ledger to
 * block double-withdrawal, the fee is computed from the rail (PayPal/Payoneer
 * 3%, domestic bank 5%), and the request enters REQUESTED. Promo-waiver tutors
 * still pay a fixed 5% withdrawal fee that is flagged for a later payback. The
 * status machine is REQUESTED -> APPROVED -> PAID; rejecting at any non-paid
 * stage reverses the held amount. The promo payback is credited as an
 * ADJUSTMENT, idempotent on unique(withdrawal_id, 'PAYBACK').</p>
 */
@Service
public class WithdrawalService {

    private final WithdrawalRepository withdrawalRepository;
    private final PromoFeeWaiverGrantRepository promoFeeWaiverGrantRepository;
    private final SettlementService settlementService;
    private final PlatformSettingsService platformSettingsService;

    public WithdrawalService(
            WithdrawalRepository withdrawalRepository,
            PromoFeeWaiverGrantRepository promoFeeWaiverGrantRepository,
            SettlementService settlementService,
            PlatformSettingsService platformSettingsService
    ) {
        this.withdrawalRepository = withdrawalRepository;
        this.promoFeeWaiverGrantRepository = promoFeeWaiverGrantRepository;
        this.settlementService = settlementService;
        this.platformSettingsService = platformSettingsService;
    }

    /**
     * Create a withdrawal request for a tutor and hold the amount on their ledger.
     * The hold reads the latest balance under a write lock, so concurrent requests
     * cannot both succeed past the balance.
     */
    @Transactional
    public Withdrawal request(Long tutorProfileId, WithdrawalMethod method, BigDecimal amountUsd, String payoutAccount) {
        Instant now = Instant.now();
        FeePolicy feePolicy = platformSettingsService.currentFeePolicy();
        boolean promoWaiver = isPromoWaiverEffective(tutorProfileId, now);
        BigDecimal feeRate = resolveFeeRate(method, feePolicy, promoWaiver);

        Withdrawal withdrawal = Withdrawal.request(tutorProfileId, method, amountUsd, feeRate, promoWaiver, payoutAccount, now);
        withdrawal = withdrawalRepository.saveAndFlush(withdrawal);

        // Hold (debit) the gross amount immediately to prevent double-withdrawal.
        settlementService.holdForWithdrawal(tutorProfileId, withdrawal.getId(), withdrawal.getRequestedAmountUsd(), now);
        return withdrawal;
    }

    @Transactional
    public Withdrawal approve(Long withdrawalId) {
        Withdrawal withdrawal = getById(withdrawalId);
        withdrawal.approve(Instant.now());
        return withdrawal;
    }

    /**
     * Mark an approved withdrawal as paid. If the tutor is a promo-waiver tutor
     * with a pending payback, the 5% fee is credited back as an ADJUSTMENT
     * (idempotent on the PAYBACK key) and the pending flag is cleared.
     */
    @Transactional
    public Withdrawal markPaid(Long withdrawalId, String payoutReference) {
        Instant now = Instant.now();
        Withdrawal withdrawal = getById(withdrawalId);
        withdrawal.markPaid(payoutReference, now);

        if (withdrawal.isPromoPaybackPending() && withdrawal.getPromoPaybackAmountUsd() != null) {
            boolean credited = settlementService.creditPromoPayback(
                    withdrawal.getTutorProfileId(),
                    withdrawal.getId(),
                    withdrawal.getPromoPaybackAmountUsd(),
                    now
            );
            if (credited) {
                withdrawal.markPaybackSettled(now);
            }
        }
        return withdrawal;
    }

    @Transactional
    public Withdrawal reject(Long withdrawalId, String reason) {
        Instant now = Instant.now();
        Withdrawal withdrawal = getById(withdrawalId);
        withdrawal.reject(reason, now);
        // Reverse the hold so the funds return to the tutor's available balance.
        settlementService.reverseWithdrawalHold(
                withdrawal.getTutorProfileId(),
                withdrawal.getId(),
                withdrawal.getRequestedAmountUsd(),
                now
        );
        return withdrawal;
    }

    @Transactional(readOnly = true)
    public List<Withdrawal> getForTutor(Long tutorProfileId) {
        return withdrawalRepository.findAllByTutorProfileIdOrderByCreatedAtDesc(tutorProfileId);
    }

    @Transactional(readOnly = true)
    public List<Withdrawal> getAll(WithdrawalStatus status) {
        return status == null
                ? withdrawalRepository.findAllByOrderByCreatedAtDesc()
                : withdrawalRepository.findAllByStatusOrderByCreatedAtDesc(status);
    }

    private Withdrawal getById(Long withdrawalId) {
        return withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Withdrawal was not found."));
    }

    private boolean isPromoWaiverEffective(Long tutorProfileId, Instant now) {
        return promoFeeWaiverGrantRepository.findByTutorProfileId(tutorProfileId)
                .map(grant -> grant.isEffective(LocalDate.ofInstant(now, ZoneOffset.UTC)))
                .orElse(false);
    }

    private BigDecimal resolveFeeRate(WithdrawalMethod method, FeePolicy feePolicy, boolean promoWaiver) {
        if (method == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Withdrawal method is required.");
        }
        // Promo tutors pay a fixed promo withdrawal fee (5%), later paid back.
        if (promoWaiver) {
            return feePolicy.promoWithdrawalFeeRate();
        }
        return switch (method) {
            case PAYPAL, PAYONEER -> feePolicy.withdrawalFeeRatePaypal();
            case DOMESTIC_BANK -> feePolicy.withdrawalFeeRateBank();
        };
    }
}
