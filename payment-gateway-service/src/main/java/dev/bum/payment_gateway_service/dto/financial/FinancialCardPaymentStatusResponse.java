package dev.bum.payment_gateway_service.dto.financial;

import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentStatusResponse;

public record FinancialCardPaymentStatusResponse(
        // 결제 번호.
        String paymentNo,

        // 결제 상태.
        CardPaymentHistoryStatus status,

        // 결제 거래 식별자.
        String transactionId
) implements FinancialPaymentResponse {

    // 기존 금융 도메인의 처리 결과를 HTTP 경계 전용 DTO로 복사한다.
    public static FinancialCardPaymentStatusResponse from(GatewayCardPaymentStatusResponse source) {

        return new FinancialCardPaymentStatusResponse(
                source.paymentNo(),
                source.status(),
                source.transactionId()
        );
    }
}
