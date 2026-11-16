package dev.bum.ticket_service.exception.payment;

public class GatewayPaymentMethodChangedException extends IllegalStateException {

    public GatewayPaymentMethodChangedException() {

        super("결제수단이 변경되었습니다. 예매 내역에서 현재 결제 정보를 확인해주세요.");
    }
}
