package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record GatewayRefundResolutionRequest(
        // 결제 환불 처리 식별자.
        @NotNull @Positive Long paymentRefundProcessId,

        // 결제 번호.
        @NotBlank String paymentNo,

        // 결제 수단.
        @NotNull PaymentMethod method,

        // 환불 금액.
        @NotNull @Positive BigDecimal refundAmount,

        // 실행 전 요청 종료 여부.
        boolean closeIfUnexecuted
) {
}
