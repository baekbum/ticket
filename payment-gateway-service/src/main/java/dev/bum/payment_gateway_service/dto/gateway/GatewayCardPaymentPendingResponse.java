package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.payment_gateway_service.dto.financial.FinancialCardPaymentPendingResponse;

public record GatewayCardPaymentPendingResponse(
        // 승인 결과를 확인 중인 원결제 번호.
        String paymentNo,

        // 기존 202 계약의 PENDING 문자열.
        String status,

        // 재승인 대신 상태 조회를 안내하는 문구.
        String message
) {

    // 금융 대기 응답의 기존 필드를 PG 응답으로 복사한다.
    public static GatewayCardPaymentPendingResponse from(FinancialCardPaymentPendingResponse response) {

        return new GatewayCardPaymentPendingResponse(response.paymentNo(), response.status(), response.message());
    }
}
