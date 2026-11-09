package dev.bum.payment_gateway_service.dto.financial;

public interface FinancialPaymentResponse {

    // 정상 금융 응답은 원거래 결제번호를 포함해야 한다. boolean 조회와 대기/오류 응답은 별도 타입이다.
    String paymentNo();
}
