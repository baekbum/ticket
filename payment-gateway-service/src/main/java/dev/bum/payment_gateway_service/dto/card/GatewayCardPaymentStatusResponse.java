package dev.bum.payment_gateway_service.dto.card;

import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;

public record GatewayCardPaymentStatusResponse(String paymentNo, CardPaymentHistoryStatus status, String transactionId) {
}
