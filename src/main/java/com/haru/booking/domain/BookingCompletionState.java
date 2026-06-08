package com.haru.booking.domain;

/**
 * Fine-grained outcome of a booking, layered on top of {@link BookingStatus}.
 * Drives settlement: which outcomes earn the tutor money and whether the
 * student's lesson credit is refundable.
 */
public enum BookingCompletionState {

    /**
     * Cancelled before the cancel window closed. Unused lesson credit is
     * refundable (actual credit issuance is handled by the credit track).
     */
    CANCELLED_NORMAL(false, true),

    /**
     * Cancelled inside the cancel window (or treated as a no-show). The lesson
     * credit is consumed, the student is refunded 0, and 100% of the lesson
     * price accrues to the tutor.
     */
    CANCELLED_LATE(true, false),

    /** Lesson ran to completion. Tutor earns. */
    COMPLETED(true, false),

    /**
     * Student did not show. Treated like a late cancel for settlement: tutor
     * earns, student refunded 0.
     */
    NO_SHOW(true, false);

    private final boolean tutorEarns;
    private final boolean creditRefundable;

    BookingCompletionState(boolean tutorEarns, boolean creditRefundable) {
        this.tutorEarns = tutorEarns;
        this.creditRefundable = creditRefundable;
    }

    public boolean tutorEarns() {
        return tutorEarns;
    }

    public boolean creditRefundable() {
        return creditRefundable;
    }
}
