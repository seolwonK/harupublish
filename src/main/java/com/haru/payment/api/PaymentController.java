package com.haru.payment.api;

import com.haru.common.response.ApiResponse;
import com.haru.common.security.HaruPrincipal;
import com.haru.payment.api.dto.CreateCheckoutRequest;
import com.haru.payment.api.dto.PaymentListResponse;
import com.haru.payment.api.dto.PaymentResponse;
import com.haru.payment.api.dto.RefundRequest;
import com.haru.payment.application.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payment", description = "튜터별 회차권 결제 요청과 환불 요청 API")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(
            summary = "결제 요청 생성",
            description = "승인된 튜터의 1/5/10회권 결제 요청을 생성합니다. 실제 PG 연동 전 상태는 PENDING입니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/checkout")
    public ApiResponse<PaymentResponse> checkout(
            @AuthenticationPrincipal HaruPrincipal principal,
            @Valid @RequestBody CreateCheckoutRequest request
    ) {
        return ApiResponse.success(paymentService.checkout(principal.userId(), request));
    }

    @Operation(summary = "내 결제 내역 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ApiResponse<PaymentListResponse> getMyPayments(@AuthenticationPrincipal HaruPrincipal principal) {
        return ApiResponse.success(paymentService.getMyPayments(principal.userId()));
    }

    @Operation(summary = "결제 상세 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentResponse> getPayment(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long paymentId
    ) {
        return ApiResponse.success(paymentService.getPayment(principal.userId(), paymentId));
    }

    @Operation(summary = "환불 요청", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{paymentId}/refund-request")
    public ApiResponse<PaymentResponse> requestRefund(
            @AuthenticationPrincipal HaruPrincipal principal,
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequest request
    ) {
        return ApiResponse.success(paymentService.requestRefund(principal.userId(), paymentId, request));
    }

    @Operation(summary = "Lemon Squeezy webhook")
    @PostMapping("/webhooks/lemonsqueezy")
    public ApiResponse<Void> lemonSqueezyWebhook(
            @RequestHeader(name = "X-Signature", required = false) String signature,
            @RequestHeader(name = "X-Event-Name", required = false) String eventName,
            @RequestBody String rawBody
    ) {
        paymentService.handleLemonSqueezyWebhook(signature, eventName, rawBody);
        return ApiResponse.success(null);
    }
}
