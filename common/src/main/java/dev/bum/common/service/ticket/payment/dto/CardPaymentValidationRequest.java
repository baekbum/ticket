package dev.bum.common.service.ticket.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record CardPaymentValidationRequest(@NotBlank String paymentNo, @NotBlank String userId) {
}
