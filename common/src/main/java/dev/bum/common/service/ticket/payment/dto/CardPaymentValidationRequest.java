package dev.bum.common.service.ticket.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record CardPaymentValidationRequest(
        // 결제 번호.
        @NotBlank String paymentNo,

        // 사용자 ID.
        @NotBlank String userId
) {
}
