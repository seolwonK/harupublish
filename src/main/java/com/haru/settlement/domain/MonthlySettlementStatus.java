package com.haru.settlement.domain;

/**
 * Lifecycle of a monthly settlement aggregate. Independent of withdrawals — this
 * is a reporting/closing rollup of a tutor's earnings for a calendar month.
 */
public enum MonthlySettlementStatus {
    OPEN,
    CLOSED,
    FINALIZED,
    PAID
}
