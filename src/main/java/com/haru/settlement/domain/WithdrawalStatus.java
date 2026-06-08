package com.haru.settlement.domain;

/**
 * Lifecycle of a tutor withdrawal request.
 *
 * <pre>
 *   REQUESTED --approve--> APPROVED --paid--> PAID
 *        |                     |
 *        +------reject---------+--> REJECTED  (held balance reversed)
 * </pre>
 */
public enum WithdrawalStatus {
    REQUESTED,
    APPROVED,
    PAID,
    REJECTED
}
