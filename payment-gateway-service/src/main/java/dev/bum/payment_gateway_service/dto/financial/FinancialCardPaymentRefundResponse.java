package dev.bum.payment_gateway_service.dto.financial;

import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;
import java.math.BigDecimal;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundResponse;

public record FinancialCardPaymentRefundResponse(
        // 결제 번호.
        String paymentNo,

        // 결제 거래 식별자.
        String transactionId,

        // 누적 환불 금액.
        BigDecimal refundedAmount,

        // 결제 환불 상태.
        CardPaymentHistoryStatus status,

        // 처리 결과 메시지.
        String message
) implements FinancialPaymentResponse {

    // 기존 금융 도메인의 처리 결과를 HTTP 경계 전용 DTO로 복사한다.
    public static FinancialCardPaymentRefundResponse from(GatewayCardPaymentRefundResponse source) {

        return new FinancialCardPaymentRefundResponse(
                source.getPaymentNo(),
                source.getTransactionId(),
                source.getRefundedAmount(),
                source.getStatus(),
                source.getMessage()
        );
    }
}
