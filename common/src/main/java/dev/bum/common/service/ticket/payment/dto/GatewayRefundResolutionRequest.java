package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record GatewayRefundResolutionRequest(
        @NotNull @Positive Long paymentRefundProcessId,
        @NotBlank String paymentNo,
        @NotNull PaymentMethod method,
        @NotNull @Positive BigDecimal refundAmount,
        boolean closeIfUnexecuted
) {
}
