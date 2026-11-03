package dev.bum.payment_gateway_service.dto.card;

import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;

public record GatewayCardPaymentStatusResponse(
        // 결제 번호.
        String paymentNo,

        // 결제 상태.
        CardPaymentHistoryStatus status,

        // 결제 거래 식별자.
        String transactionId
) {
}
