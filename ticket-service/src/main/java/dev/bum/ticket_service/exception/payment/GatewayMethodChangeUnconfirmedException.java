package dev.bum.ticket_service.exception.payment;

public class GatewayMethodChangeUnconfirmedException extends IllegalStateException {

    public GatewayMethodChangeUnconfirmedException(Throwable cause) {

        super("기존 카드 승인 또는 결제수단 변경 결과를 확인하지 못했습니다. 같은 요청으로 다시 시도해주세요.", cause);
    }
}
