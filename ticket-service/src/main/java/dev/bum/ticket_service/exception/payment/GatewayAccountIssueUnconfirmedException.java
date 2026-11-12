package dev.bum.ticket_service.exception.payment;

public class GatewayAccountIssueUnconfirmedException extends RuntimeException {

    // 재시도 한도 소진은 PG 발급 취소나 원거래 실패를 뜻하지 않는다.
    public GatewayAccountIssueUnconfirmedException(Throwable cause) {

        super("가상계좌 발급 결과를 확인하지 못했습니다. 같은 결제 요청으로 다시 시도하거나 예매 내역을 확인해주세요.", cause);
    }
}
