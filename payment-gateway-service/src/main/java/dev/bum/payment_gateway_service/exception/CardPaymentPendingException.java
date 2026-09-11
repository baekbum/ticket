package dev.bum.payment_gateway_service.exception;

import lombok.Getter;

@Getter
public class CardPaymentPendingException extends RuntimeException {
    private final String paymentNo;

    public CardPaymentPendingException(String paymentNo, Throwable cause) {
        super("카드 승인 후 예매 결과를 확인하고 있습니다. 새 결제를 시작하지 말고 상태를 조회해주세요.", cause);
        this.paymentNo = paymentNo;
    }
}
