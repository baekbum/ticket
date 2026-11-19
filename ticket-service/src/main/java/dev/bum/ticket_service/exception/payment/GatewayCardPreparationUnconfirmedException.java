package dev.bum.ticket_service.exception.payment;

public class GatewayCardPreparationUnconfirmedException extends IllegalStateException {

    public GatewayCardPreparationUnconfirmedException(Throwable cause) {

        super("카드 결제 준비 결과를 확인하지 못했습니다. 같은 결제 요청으로 다시 시도해주세요.", cause);
    }
}
