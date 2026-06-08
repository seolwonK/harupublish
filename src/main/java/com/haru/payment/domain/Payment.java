package com.haru.payment.domain;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.settings.domain.FeePolicy;
import com.haru.tutor.domain.TutorProfile;
import com.haru.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    public static final String V1_CURRENCY = "USD";
    public static final int LESSON_DURATION_25 = 25;
    public static final int LESSON_DURATION_50 = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private UserAccount student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_profile_id", nullable = false)
    private TutorProfile tutorProfile;

    @Column(name = "lesson_duration_minutes", nullable = false)
    private int lessonDurationMinutes;

    @Column(name = "lesson_pack_count", nullable = false)
    private int lessonPackCount;

    @Column(name = "unit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitAmount;

    @Column(name = "subtotal_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "student_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal studentFeeAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "provider", length = 40)
    private String provider;

    @Column(name = "provider_checkout_id", length = 120)
    private String providerCheckoutId;

    @Column(name = "provider_checkout_url", length = 1000)
    private String providerCheckoutUrl;

    @Column(name = "provider_order_id", length = 120)
    private String providerOrderId;

    @Column(name = "provider_order_identifier", length = 120)
    private String providerOrderIdentifier;

    @Column(name = "applied_student_fee_rate", precision = 6, scale = 4)
    private BigDecimal appliedStudentFeeRate;

    @Column(name = "applied_five_pack_discount_rate", precision = 6, scale = 4)
    private BigDecimal appliedFivePackDiscountRate;

    @Column(name = "applied_ten_pack_discount_rate", precision = 6, scale = 4)
    private BigDecimal appliedTenPackDiscountRate;

    @Column(name = "settings_version")
    private Integer settingsVersion;

    @Column(name = "display_currency", length = 3)
    private String displayCurrency;

    @Column(name = "fx_rate_used", precision = 18, scale = 8)
    private BigDecimal fxRateUsed;

    @Column(name = "fx_rate_source", length = 40)
    private String fxRateSource;

    @Column(name = "fx_captured_at")
    private Instant fxCapturedAt;

    @Column(name = "refund_credit_amount_usd", precision = 10, scale = 2)
    private BigDecimal refundCreditAmountUsd;

    @Column(name = "refund_approved_at")
    private Instant refundApprovedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    private Payment(
            UserAccount student,
            TutorProfile tutorProfile,
            int lessonDurationMinutes,
            int lessonPackCount,
            BigDecimal unitAmount,
            PaymentMethod paymentMethod,
            FeePolicy feePolicy
    ) {
        validateLessonDuration(lessonDurationMinutes);
        validateLessonPackCount(lessonPackCount);
        validateUnitAmount(unitAmount);
        if (paymentMethod == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Payment method is required.");
        }
        if (feePolicy == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Fee policy is required for checkout.");
        }

        this.student = student;
        this.tutorProfile = tutorProfile;
        this.lessonDurationMinutes = lessonDurationMinutes;
        this.lessonPackCount = lessonPackCount;

        // Model 1 (markup) pricing — every multiply is rounded with money() HALF_UP at scale 2.
        BigDecimal discountRate = feePolicy.discountRate(lessonPackCount);
        this.unitAmount = money(unitAmount);
        this.subtotalAmount = money(this.unitAmount.multiply(BigDecimal.valueOf(lessonPackCount)));
        this.discountAmount = money(this.subtotalAmount.multiply(discountRate));
        // discounted = tutor gross (pack discount is shared with the tutor).
        BigDecimal discountedAmount = this.subtotalAmount.subtract(this.discountAmount);
        // studentFee = platform student markup on the discounted tutor gross.
        this.studentFeeAmount = money(discountedAmount.multiply(feePolicy.studentFeeRate()));
        // total = single student-facing price ("10% included").
        this.totalAmount = money(discountedAmount.add(this.studentFeeAmount));

        // Snapshot the rates/version that were in effect (no retroactive repricing).
        this.appliedStudentFeeRate = feePolicy.studentFeeRate();
        this.appliedFivePackDiscountRate = feePolicy.fivePackDiscountRate();
        this.appliedTenPackDiscountRate = feePolicy.tenPackDiscountRate();
        this.settingsVersion = feePolicy.settingVersion();

        this.currency = V1_CURRENCY;
        this.displayCurrency = V1_CURRENCY;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Payment checkout(
            UserAccount student,
            TutorProfile tutorProfile,
            int lessonDurationMinutes,
            int lessonPackCount,
            BigDecimal unitAmount,
            PaymentMethod paymentMethod,
            FeePolicy feePolicy
    ) {
        return new Payment(student, tutorProfile, lessonDurationMinutes, lessonPackCount, unitAmount, paymentMethod, feePolicy);
    }

    public void requestRefund(String reason) {
        if (status != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only paid payments can request a refund.");
        }
        status = PaymentStatus.REFUND_REQUESTED;
        refundReason = reason;
        touch();
    }

    public void markPaid() {
        status = PaymentStatus.PAID;
        touch();
    }

    public void attachCheckout(String provider, String checkoutId, String checkoutUrl) {
        this.provider = provider;
        this.providerCheckoutId = checkoutId;
        this.providerCheckoutUrl = checkoutUrl;
        touch();
    }

    /**
     * Provider-confirmed payment: marks the payment PAID and records the provider
     * order ids.
     *
     * <p>Idempotency / replay guard (defect #8c): a payment that is already PAID or
     * in a terminal state (REFUNDED / FAILED / CANCELLED) is left untouched and
     * {@code false} is returned, so a re-delivered {@code order_created} webhook (or
     * a duplicate of the same order) never re-runs {@code markPaid}. Returns
     * {@code true} only when this call actually performed the PAID transition.</p>
     */
    public boolean markPaidByProvider(String providerOrderId, String providerOrderIdentifier) {
        if (isTerminal() || status == PaymentStatus.PAID) {
            return false;
        }
        this.providerOrderId = providerOrderId;
        this.providerOrderIdentifier = providerOrderIdentifier;
        this.status = PaymentStatus.PAID;
        touch();
        return true;
    }

    /**
     * Whether this payment has reached a state from which the provider should not
     * move it back to PAID (refunded / failed / cancelled). Used to make webhook /
     * polling status transitions idempotent and replay-safe.
     */
    public boolean isTerminal() {
        return status == PaymentStatus.REFUNDED
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCELLED;
    }

    /**
     * Provider real (cash) refund: marks the payment REFUNDED and records the
     * provider order ids. <b>No Haru credit is issued</b> on this path — the money
     * was returned by the payment provider (e.g. a dispute / chargeback), so the
     * student is not also credited (that would double-refund).
     *
     * <p>State guard (defect #6): a payment already REFUNDED is left untouched and
     * {@code false} is returned, so a re-delivered {@code order_refunded} webhook or
     * a polling sync that re-observes a refunded order is a no-op. This also means a
     * payment refunded as <em>credit</em> (admin approval) is never re-stamped as a
     * provider refund. Returns {@code true} only when this call actually performed
     * the REFUNDED transition.</p>
     */
    public boolean markRefundedByProvider(String providerOrderId, String providerOrderIdentifier) {
        if (status == PaymentStatus.REFUNDED) {
            return false;
        }
        this.providerOrderId = providerOrderId;
        this.providerOrderIdentifier = providerOrderIdentifier;
        this.status = PaymentStatus.REFUNDED;
        touch();
        return true;
    }

    /**
     * Admin approval of a refund issued as Haru credit (no cash-out). Records the
     * credited USD amount and moves the payment to REFUNDED so it cannot be
     * approved again, which (together with the provider path) blocks double refunds.
     */
    public void approveRefundAsCredit(BigDecimal creditAmountUsd, Instant now) {
        if (status == PaymentStatus.REFUNDED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Payment was already refunded.");
        }
        this.refundCreditAmountUsd = creditAmountUsd;
        this.refundApprovedAt = now;
        this.status = PaymentStatus.REFUNDED;
        touch();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
        touch();
    }

    private static void validateLessonDuration(int lessonDurationMinutes) {
        // Decision A (MVP): only 25-minute lessons are purchasable. 50-minute
        // pricing/booking ships in a later round.
        if (lessonDurationMinutes != LESSON_DURATION_25) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only 25-minute lessons can be purchased right now.");
        }
    }

    private static void validateLessonPackCount(int lessonPackCount) {
        if (lessonPackCount != 1 && lessonPackCount != 5 && lessonPackCount != 10) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "lessonPackCount must be 1, 5, or 10.");
        }
    }

    private static void validateUnitAmount(BigDecimal unitAmount) {
        if (unitAmount == null || unitAmount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson price must be greater than zero.");
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UserAccount getStudent() {
        return student;
    }

    public TutorProfile getTutorProfile() {
        return tutorProfile;
    }

    public int getLessonDurationMinutes() {
        return lessonDurationMinutes;
    }

    public int getLessonPackCount() {
        return lessonPackCount;
    }

    public BigDecimal getUnitAmount() {
        return unitAmount;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getStudentFeeAmount() {
        return studentFeeAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * Tutor gross for the whole pack = subtotal - discount (the pack discount is
     * shared with the tutor). The student fee is on top of this, not part of it.
     */
    public BigDecimal getDiscountedAmount() {
        return subtotalAmount.subtract(discountAmount);
    }

    /**
     * Per-lesson tutor gross in USD (discounted / packCount), rounded HALF_UP to
     * 2 decimals. This is the gross a single completed/earned lesson contributes
     * to settlement.
     *
     * <p><b>Caution — rounding leak:</b> for packs whose discounted value is not
     * evenly divisible the naive {@code discounted / packCount} per-unit times
     * {@code packCount} does NOT equal {@code discounted} (e.g. 475 / 5 = 95.00 is
     * exact, but a discounted of 158.32 / 5 = 31.66 * 5 = 158.30 leaks 0.02). For
     * settlement / refund the {@link #perLessonSlots()} list below distributes the
     * residual cents so the slot sum is exact. This single-value accessor is kept
     * for display / legacy fallback only.</p>
     */
    public BigDecimal perLessonDiscountedAmount() {
        return getDiscountedAmount()
                .divide(BigDecimal.valueOf(lessonPackCount), 2, RoundingMode.HALF_UP);
    }

    /**
     * Expand this payment's tutor gross ({@code discounted}) into exactly
     * {@code lessonPackCount} per-lesson "slots" whose sum equals {@code discounted}
     * to the cent (largest-remainder allocation).
     *
     * <p>Each slot starts at {@code base = floor(discounted / packCount, 2)}; the
     * residual cents {@code discounted - base * packCount} are distributed one cent
     * at a time, front to back. This guarantees the settlement invariant
     * {@code Σ(per-lesson gross) == payment.discounted} for every pack, eliminating
     * the per-lesson rounding leak (defect #3) while keeping FIFO settlement
     * (defect #2) attributable to the exact paying pack.</p>
     */
    public java.util.List<BigDecimal> perLessonSlots() {
        return distributeSlots(getDiscountedAmount(), lessonPackCount);
    }

    /**
     * Largest-remainder split of {@code total} (scale 2) into {@code units} parts
     * that sum exactly to {@code total}. {@code base = floor(total/units, 2)}; the
     * leftover cents are added 0.01 at a time to the leading slots. Shared by
     * settlement (per booking) and refund (per unused unit) so both agree.
     */
    public static java.util.List<BigDecimal> distributeSlots(BigDecimal total, int units) {
        if (units <= 0) {
            return java.util.List.of();
        }
        BigDecimal scaledTotal = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = scaledTotal.divide(BigDecimal.valueOf(units), 2, RoundingMode.FLOOR);
        // residualCents = (scaledTotal - base*units) expressed in whole cents.
        BigDecimal residual = scaledTotal.subtract(base.multiply(BigDecimal.valueOf(units)));
        int residualCents = residual.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        BigDecimal oneCent = new BigDecimal("0.01");

        java.util.List<BigDecimal> slots = new java.util.ArrayList<>(units);
        for (int i = 0; i < units; i++) {
            BigDecimal slot = (i < residualCents) ? base.add(oneCent) : base;
            slots.add(slot);
        }
        return slots;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderCheckoutId() {
        return providerCheckoutId;
    }

    public String getProviderCheckoutUrl() {
        return providerCheckoutUrl;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public String getProviderOrderIdentifier() {
        return providerOrderIdentifier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public BigDecimal getAppliedStudentFeeRate() {
        return appliedStudentFeeRate;
    }

    public BigDecimal getAppliedFivePackDiscountRate() {
        return appliedFivePackDiscountRate;
    }

    public BigDecimal getAppliedTenPackDiscountRate() {
        return appliedTenPackDiscountRate;
    }

    public Integer getSettingsVersion() {
        return settingsVersion;
    }

    public String getDisplayCurrency() {
        return displayCurrency;
    }

    public BigDecimal getFxRateUsed() {
        return fxRateUsed;
    }

    public String getFxRateSource() {
        return fxRateSource;
    }

    public Instant getFxCapturedAt() {
        return fxCapturedAt;
    }

    public BigDecimal getRefundCreditAmountUsd() {
        return refundCreditAmountUsd;
    }

    public Instant getRefundApprovedAt() {
        return refundApprovedAt;
    }
}
