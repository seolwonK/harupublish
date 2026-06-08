package com.haru.settlement.domain;

/**
 * Type of a tutor earning ledger entry. The ledger is the source of truth for a
 * tutor's "cyber money" (USD) balance.
 */
public enum EarningEntryType {

    /** A completed lesson credited the tutor net = gross * (1 - platformFeeRate). */
    LESSON_EARNED,

    /** A late-cancel / no-show lesson credited the tutor (student refunded 0). */
    NO_SHOW_EARNED,

    /** A withdrawal request held (debited) the balance to prevent double-withdrawal (-). */
    WITHDRAWAL_HOLD,

    /** A rejected withdrawal reversed (re-credited) the held amount (+). */
    WITHDRAWAL_REVERSED,

    /** A manual or promo payback adjustment credited the tutor (+). */
    ADJUSTMENT
}
