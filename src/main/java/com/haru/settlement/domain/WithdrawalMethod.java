package com.haru.settlement.domain;

/**
 * Payout rail for a tutor withdrawal. Drives the (non-promo) fee rate:
 * PayPal/Payoneer = 3%, domestic bank = 5%.
 */
public enum WithdrawalMethod {
    PAYPAL,
    PAYONEER,
    DOMESTIC_BANK
}
