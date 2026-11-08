package dev.bum.payment_gateway_service.exception;

public class GatewayPaymentConflictException extends RuntimeException {

    public GatewayPaymentConflictException() {

        super("같은 가맹점 결제번호로 다른 결제 정보를 요청할 수 없습니다.");
    }

    public GatewayPaymentConflictException(String message) {

        super(message);
    }
}
