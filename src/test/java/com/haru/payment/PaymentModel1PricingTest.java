package com.haru.payment;

import com.haru.common.exception.BusinessException;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentMethod;
import com.haru.settings.domain.FeePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test for the model 1 (markup) pricing inside {@link Payment}.
 * No Spring context — the domain math and the duration guard are exercised
 * directly against a hand-built {@link FeePolicy}.
 */
class PaymentModel1PricingTest {

    private static final FeePolicy POLICY = new FeePolicy(
            new BigDecimal("0.1000"), // studentFeeRate
            new BigDecimal("0.1500"), // platformFeeRate
            new BigDecimal("0.0300"), // withdrawalFeeRatePaypal
            new BigDecimal("0.0500"), // withdrawalFeeRateBank
            new BigDecimal("0.0500"), // fivePackDiscountRate
            new BigDecimal("0.1000"), // tenPackDiscountRate
            new BigDecimal("0.0500"), // promoWithdrawalFeeRate
            3,                         // cancelWindowHours
            12,                        // creditExpiryMonths
            1                          // settingVersion
    );

    @Test
    void singlePackHasNoDiscountAndAddsTenPercentStudentFee() {
        Payment payment = Payment.checkout(null, null, 25, 1, new BigDecimal("100.00"), PaymentMethod.LEMON_SQUEEZY, POLICY);

        assertThat(payment.getSubtotalAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(payment.getStudentFeeAmount()).isEqualByComparingTo("10.00");
        assertThat(payment.getTotalAmount()).isEqualByComparingTo("110.00");
        assertThat(payment.getAppliedStudentFeeRate()).isEqualByComparingTo("0.1000");
        assertThat(payment.getSettingsVersion()).isEqualTo(1);
        assertThat(payment.getCurrency()).isEqualTo("USD");
        assertThat(payment.getDisplayCurrency()).isEqualTo("USD");
    }

    @Test
    void fivePackSharesDiscountThenMarksUpStudentFee() {
        Payment payment = Payment.checkout(null, null, 25, 5, new BigDecimal("100.00"), PaymentMethod.LEMON_SQUEEZY, POLICY);

        // subtotal=500, discount=500*0.05=25, discounted=475, fee=475*0.10=47.50, total=522.50
        assertThat(payment.getSubtotalAmount()).isEqualByComparingTo("500.00");
        assertThat(payment.getDiscountAmount()).isEqualByComparingTo("25.00");
        assertThat(payment.getStudentFeeAmount()).isEqualByComparingTo("47.50");
        assertThat(payment.getTotalAmount()).isEqualByComparingTo("522.50");
    }

    @Test
    void tenPackUsesTenPercentDiscount() {
        Payment payment = Payment.checkout(null, null, 25, 10, new BigDecimal("100.00"), PaymentMethod.LEMON_SQUEEZY, POLICY);

        // subtotal=1000, discount=1000*0.10=100, discounted=900, fee=900*0.10=90, total=990
        assertThat(payment.getSubtotalAmount()).isEqualByComparingTo("1000.00");
        assertThat(payment.getDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getStudentFeeAmount()).isEqualByComparingTo("90.00");
        assertThat(payment.getTotalAmount()).isEqualByComparingTo("990.00");
    }

    @Test
    void roundingUsesHalfUpAtScaleTwo() {
        // unit 33.33, 5-pack: subtotal=166.65, discount=166.65*0.05=8.3325 -> 8.33,
        // discounted=158.32, fee=158.32*0.10=15.832 -> 15.83, total=174.15
        Payment payment = Payment.checkout(null, null, 25, 5, new BigDecimal("33.33"), PaymentMethod.LEMON_SQUEEZY, POLICY);

        assertThat(payment.getSubtotalAmount()).isEqualByComparingTo("166.65");
        assertThat(payment.getDiscountAmount()).isEqualByComparingTo("8.33");
        assertThat(payment.getStudentFeeAmount()).isEqualByComparingTo("15.83");
        assertThat(payment.getTotalAmount()).isEqualByComparingTo("174.15");
    }

    @Test
    void perLessonSlotsSumExactlyToDiscountedForEvenPack() {
        // 5-pack @100 -> discounted=475 -> 5 even slots of 95.00.
        Payment payment = Payment.checkout(null, null, 25, 5, new BigDecimal("100.00"), PaymentMethod.LEMON_SQUEEZY, POLICY);

        java.util.List<BigDecimal> slots = payment.perLessonSlots();
        assertThat(slots).hasSize(5);
        assertThat(slots).allSatisfy(s -> assertThat(s).isEqualByComparingTo("95.00"));
        assertThat(sum(slots)).isEqualByComparingTo(payment.getDiscountedAmount());
        assertThat(sum(slots)).isEqualByComparingTo("475.00");
    }

    @Test
    void perLessonSlotsDistributeResidualCentsSoSumIsExact() {
        // unit 33.33, 5-pack: discounted = 158.32. 158.32 / 5 = 31.664 -> floor 31.66.
        // base*5 = 158.30, residual = 0.02 cents -> first 2 slots get +0.01 = 31.67.
        Payment payment = Payment.checkout(null, null, 25, 5, new BigDecimal("33.33"), PaymentMethod.LEMON_SQUEEZY, POLICY);

        assertThat(payment.getDiscountedAmount()).isEqualByComparingTo("158.32");
        java.util.List<BigDecimal> slots = payment.perLessonSlots();
        assertThat(slots).hasSize(5);
        // Largest-remainder: leading slots absorb the residual cents.
        assertThat(slots.get(0)).isEqualByComparingTo("31.67");
        assertThat(slots.get(1)).isEqualByComparingTo("31.67");
        assertThat(slots.get(2)).isEqualByComparingTo("31.66");
        assertThat(slots.get(3)).isEqualByComparingTo("31.66");
        assertThat(slots.get(4)).isEqualByComparingTo("31.66");
        // Invariant: Σ(per-lesson gross) == discounted (no rounding leak, defect #3).
        assertThat(sum(slots)).isEqualByComparingTo(payment.getDiscountedAmount());
        assertThat(sum(slots)).isEqualByComparingTo("158.32");
        // The naive per-unit*pack would leak: 31.66 * 5 = 158.30 != 158.32.
        assertThat(payment.perLessonDiscountedAmount().multiply(new BigDecimal("5")))
                .isEqualByComparingTo("158.30");
    }

    @Test
    void perLessonSlotsSumExactlyForTenPackWithResidual() {
        // unit 33.33, 10-pack: subtotal=333.30, discount=33.33, discounted=299.97.
        // 299.97 / 10 = 29.997 -> floor 29.99. base*10 = 299.90, residual 0.07 -> 7 slots +0.01.
        Payment payment = Payment.checkout(null, null, 25, 10, new BigDecimal("33.33"), PaymentMethod.LEMON_SQUEEZY, POLICY);

        assertThat(payment.getDiscountedAmount()).isEqualByComparingTo("299.97");
        java.util.List<BigDecimal> slots = payment.perLessonSlots();
        assertThat(slots).hasSize(10);
        long centsAt30 = slots.stream().filter(s -> s.compareTo(new BigDecimal("30.00")) == 0).count();
        long centsAt2999 = slots.stream().filter(s -> s.compareTo(new BigDecimal("29.99")) == 0).count();
        assertThat(centsAt30).isEqualTo(7);
        assertThat(centsAt2999).isEqualTo(3);
        assertThat(sum(slots)).isEqualByComparingTo(payment.getDiscountedAmount());
        assertThat(sum(slots)).isEqualByComparingTo("299.97");
    }

    private static BigDecimal sum(java.util.List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total;
    }

    @Test
    void fiftyMinuteLessonIsRejectedInMvp() {
        assertThatThrownBy(() ->
                Payment.checkout(null, null, 50, 1, new BigDecimal("180.00"), PaymentMethod.LEMON_SQUEEZY, POLICY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void feePolicyRequiredForCheckout() {
        assertThatThrownBy(() ->
                Payment.checkout(null, null, 25, 1, new BigDecimal("100.00"), PaymentMethod.LEMON_SQUEEZY, null))
                .isInstanceOf(BusinessException.class);
    }
}
