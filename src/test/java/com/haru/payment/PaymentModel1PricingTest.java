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
