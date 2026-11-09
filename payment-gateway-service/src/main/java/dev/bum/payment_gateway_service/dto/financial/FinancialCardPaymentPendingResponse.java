package dev.bum.payment_gateway_service.dto.financial;

public record FinancialCardPaymentPendingResponse(
        // 승인 결과를 확인 중인 기존 결제번호.
        String paymentNo,

        // 기존 202 계약의 PENDING 문자열.
        String status,

        // 새 승인을 반복하지 않고 상태 확인을 안내하는 문구.
        String message
) {
}
