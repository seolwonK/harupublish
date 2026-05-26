package com.haru.payment.application;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.ForbiddenException;
import com.haru.common.exception.NotFoundException;
import com.haru.payment.api.dto.CreateCheckoutRequest;
import com.haru.payment.api.dto.PaymentListResponse;
import com.haru.payment.api.dto.PaymentResponse;
import com.haru.payment.api.dto.RefundRequest;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentMethod;
import com.haru.payment.domain.PaymentStatus;
import com.haru.payment.infra.LemonSqueezyCheckout;
import com.haru.payment.infra.LemonSqueezyClient;
import com.haru.payment.infra.LemonSqueezyClient.LemonSqueezyOrder;
import com.haru.payment.infra.LemonSqueezyProperties;
import com.haru.payment.infra.PaymentRepository;
import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.tutor.infra.TutorProfileRepository;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    private final UserAccountRepository userAccountRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final PaymentRepository paymentRepository;
    private final LemonSqueezyClient lemonSqueezyClient;
    private final LemonSqueezyProperties lemonSqueezyProperties;
    private final ObjectMapper objectMapper;

    public PaymentService(
            UserAccountRepository userAccountRepository,
            TutorProfileRepository tutorProfileRepository,
            PaymentRepository paymentRepository,
            LemonSqueezyClient lemonSqueezyClient,
            LemonSqueezyProperties lemonSqueezyProperties,
            ObjectMapper objectMapper
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tutorProfileRepository = tutorProfileRepository;
        this.paymentRepository = paymentRepository;
        this.lemonSqueezyClient = lemonSqueezyClient;
        this.lemonSqueezyProperties = lemonSqueezyProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse checkout(Long studentUserId, CreateCheckoutRequest request) {
        if (request.paymentMethod() != PaymentMethod.LEMON_SQUEEZY) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only Lemon Squeezy is supported for payments right now.");
        }

        UserAccount student = userAccountRepository.findWithRolesById(studentUserId)
                .orElseThrow(() -> new NotFoundException("User was not found."));
        TutorProfile tutorProfile = tutorProfileRepository.findByIdAndStatus(request.tutorProfileId(), TutorProfileStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));

        if (tutorProfile.getUser().getId().equals(studentUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Tutors cannot buy their own lesson packs.");
        }

        Payment payment = paymentRepository.save(Payment.checkout(
                student,
                tutorProfile,
                request.lessonDurationMinutes(),
                request.lessonPackCount(),
                unitAmount(tutorProfile, request.lessonDurationMinutes()),
                PaymentMethod.LEMON_SQUEEZY
        ));

        if (lemonSqueezyClient.isEnabled()) {
            LemonSqueezyCheckout checkout = lemonSqueezyClient.createCheckout(payment);
            payment.attachCheckout(LemonSqueezyClient.PROVIDER, checkout.id(), checkout.url());
        } else if (lemonSqueezyProperties.isMockPaidCheckoutEnabled()) {
            payment.markPaid();
        } else {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lemon Squeezy payments are not configured. Set LEMON_SQUEEZY_ENABLED=true and provide LEMON_SQUEEZY_API_KEY, LEMON_SQUEEZY_STORE_ID, and LEMON_SQUEEZY_VARIANT_ID."
            );
        }

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentListResponse getMyPayments(Long userId) {
        syncPendingPayments(userId);
        return PaymentListResponse.from(paymentRepository.findAllByStudentIdOrderByCreatedAtDesc(userId));
    }

    @Transactional
    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = getPaymentWithAccess(userId, paymentId);
        syncPaymentStatus(payment);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse requestRefund(Long userId, Long paymentId, RefundRequest request) {
        Payment payment = getPaymentWithAccess(userId, paymentId);
        payment.requestRefund(request == null ? null : request.reason());
        return PaymentResponse.from(payment);
    }

    @Transactional
    public void handleLemonSqueezyWebhook(String signature, String eventName, String rawBody) {
        verifyLemonSqueezySignature(signature, rawBody);

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String resolvedEventName = StringUtils.hasText(eventName)
                    ? eventName
                    : root.path("meta").path("event_name").asText("");
            JsonNode customData = root.path("meta").path("custom_data");
            Long paymentId = paymentIdFrom(customData.path("payment_id"));
            if (paymentId == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Webhook custom_data.payment_id is missing.");
            }

            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new NotFoundException("Payment was not found."));
            JsonNode data = root.path("data");
            JsonNode attributes = data.path("attributes");
            String orderId = data.path("id").asText(null);
            String orderIdentifier = attributes.path("identifier").asText(null);
            boolean refunded = attributes.path("refunded").asBoolean(false);
            String status = attributes.path("status").asText("");

            if ("order_refunded".equals(resolvedEventName) || refunded) {
                payment.markRefundedByProvider(orderId, orderIdentifier);
                return;
            }
            if ("order_created".equals(resolvedEventName) || "order_updated".equals(resolvedEventName)) {
                if ("paid".equalsIgnoreCase(status)) {
                    payment.markPaidByProvider(orderId, orderIdentifier);
                } else if ("failed".equalsIgnoreCase(status)) {
                    payment.markFailed();
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid Lemon Squeezy webhook payload.");
        }
    }

    private Long paymentIdFrom(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String value = node.asText("");
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Payment getPaymentWithAccess(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findWithDetailsById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment was not found."));
        if (!payment.getStudent().getId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "You do not have access to this payment.");
        }
        return payment;
    }

    private BigDecimal unitAmount(TutorProfile tutorProfile, Integer lessonDurationMinutes) {
        if (lessonDurationMinutes == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "lessonDurationMinutes is required.");
        }
        if (lessonDurationMinutes == Payment.LESSON_DURATION_25) {
            return tutorProfile.getLessonPrice25Amount();
        }
        if (lessonDurationMinutes == Payment.LESSON_DURATION_50) {
            return tutorProfile.getLessonPrice50Amount();
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "lessonDurationMinutes must be 25 or 50.");
    }

    private void syncPendingPayments(Long userId) {
        List<Payment> pendingPayments = paymentRepository.findAllByStudentIdAndStatusOrderByCreatedAtDesc(userId, PaymentStatus.PENDING);
        if (pendingPayments.isEmpty()) {
            return;
        }

        List<LemonSqueezyOrder> userOrders = lemonSqueezyClient.listOrdersByUserEmail(pendingPayments.get(0).getStudent().getEmail());
        pendingPayments.forEach(payment -> syncPaymentStatus(payment, userOrders));

        List<Payment> stillPending = pendingPayments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .toList();
        if (!stillPending.isEmpty()) {
            List<LemonSqueezyOrder> recentOrders = lemonSqueezyClient.listRecentOrders();
            stillPending.forEach(payment -> syncPaymentStatus(payment, recentOrders));
        }
    }

    private void syncPaymentStatus(Payment payment) {
        if (!canSyncPaymentStatus(payment)) {
            return;
        }
        syncPaymentStatus(payment, lemonSqueezyClient.listOrdersByUserEmail(payment.getStudent().getEmail()));
    }

    private void syncPaymentStatus(Payment payment, List<LemonSqueezyOrder> userOrders) {
        if (!canSyncPaymentStatus(payment)) {
            return;
        }

        Optional<LemonSqueezyOrder> matchedOrder = StringUtils.hasText(payment.getProviderOrderId())
                ? lemonSqueezyClient.retrieveOrder(payment.getProviderOrderId())
                : findMatchingOrder(payment, userOrders);

        matchedOrder.ifPresent(order -> applyProviderOrder(payment, order));
    }

    private boolean canSyncPaymentStatus(Payment payment) {
        return payment.getStatus() == PaymentStatus.PENDING
                && payment.getPaymentMethod() == PaymentMethod.LEMON_SQUEEZY;
    }

    private Optional<LemonSqueezyOrder> findMatchingOrder(Payment payment, List<LemonSqueezyOrder> userOrders) {
        long expectedProviderTotal = lemonSqueezyClient.toProviderMinorUnits(payment.getTotalAmount());
        String expectedVariantId = lemonSqueezyProperties.getVariantId();
        boolean testMode = lemonSqueezyProperties.isTestMode();

        return userOrders.stream()
                .filter(order -> order.testMode() == testMode)
                .filter(order -> expectedVariantId.equals(order.variantId()))
                .filter(order -> !order.createdAt().isBefore(payment.getCreatedAt().minus(Duration.ofMinutes(2))))
                .filter(order -> matchesExpectedTotal(order, expectedProviderTotal))
                .filter(order -> !StringUtils.hasText(order.id()) || !paymentRepository.existsByProviderOrderIdAndIdNot(order.id(), payment.getId()))
                .sorted(Comparator.comparing(LemonSqueezyOrder::createdAt))
                .findFirst();
    }

    private boolean matchesExpectedTotal(LemonSqueezyOrder order, long expectedProviderTotal) {
        return Math.abs(order.total() - expectedProviderTotal) <= 1000L
                || (order.totalUsd() > 0 && Math.abs(order.totalUsd() - expectedProviderTotal) <= 1000L);
    }

    private void applyProviderOrder(Payment payment, LemonSqueezyOrder order) {
        if (order.refunded() || "refunded".equalsIgnoreCase(order.status()) || "partial_refund".equalsIgnoreCase(order.status())) {
            payment.markRefundedByProvider(order.id(), order.identifier());
            return;
        }
        if ("paid".equalsIgnoreCase(order.status())) {
            payment.markPaidByProvider(order.id(), order.identifier());
            return;
        }
        if ("failed".equalsIgnoreCase(order.status()) || "fraudulent".equalsIgnoreCase(order.status())) {
            payment.markFailed();
        }
    }

    private void verifyLemonSqueezySignature(String signature, String rawBody) {
        if (!StringUtils.hasText(lemonSqueezyProperties.getSigningSecret())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lemon Squeezy signing secret is not configured.");
        }
        if (!StringUtils.hasText(signature)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lemon Squeezy signature is missing.");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(lemonSqueezyProperties.getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = toHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid Lemon Squeezy signature.");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Could not verify Lemon Squeezy signature.");
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }
}
