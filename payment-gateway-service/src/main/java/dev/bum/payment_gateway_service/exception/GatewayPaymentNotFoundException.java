package dev.bum.payment_gateway_service.exception;

public class GatewayPaymentNotFoundException extends RuntimeException {

    public GatewayPaymentNotFoundException() {

        super("PG 거래를 찾을 수 없습니다.");
    }
}
