package com.haru.credit.domain;

/**
 * Type of a Haru credit ledger entry. The ledger is the source of truth for a
 * student's refund-only virtual currency (USD-denominated, no cash-out).
 */
public enum HaruCreditEntryType {

    /** A refund was approved and issued as credit (+). */
    REFUND_ISSUED,

    /** Credit was spent on a lesson pack (-), consumed FIFO by issuance order. */
    SPENT,

    /** The whole balance expired after 12 months of account inactivity (-). */
    EXPIRED
}
