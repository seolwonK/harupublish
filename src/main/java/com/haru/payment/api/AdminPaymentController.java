package com.haru.payment.api;

import com.haru.common.response.ApiResponse;
import com.haru.payment.api.dto.PaymentResponse;
import com.haru.payment.application.PaymentRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
@Tag(name = "AdminPayment", description = "관리자 환불 승인(=Haru 크레딧 발급) API")
public class AdminPaymentController {

    private final PaymentRefundService paymentRefundService;

    public AdminPaymentController(PaymentRefundService paymentRefundService) {
        this.paymentRefundService = paymentRefundService;
    }

    @Operation(
            summary = "환불 승인 (크레딧 발급)",
            description = "미사용 회차 * 회차당 USD 단가를 Haru 크레딧으로 발급하고 결제를 REFUNDED로 전환합니다. provider 실환불과 분기되어 이중환불을 차단합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/{paymentId}/refund-approve")
    public ApiResponse<PaymentResponse> approveRefund(@PathVariable Long paymentId) {
        return ApiResponse.success(paymentRefundService.approveRefund(paymentId));
    }
}
