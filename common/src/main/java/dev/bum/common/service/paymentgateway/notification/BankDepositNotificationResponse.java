package dev.bum.common.service.paymentgateway.notification;

import java.math.BigDecimal;

public record BankDepositNotificationResponse(
        // 수신한 계약 버전.
        int version,

        // 수신한 이벤트 식별자.
        String eventId,

        // 수신한 가맹점 식별자.
        Long merchantId,

        // 수신한 PG 거래.
        String pgPaymentId,

        // 수신한 가맹점 결제번호.
        String merchantPaymentNo,

        // 수신한 은행 계좌.
        String accountNumber,

        // 수신한 최초 금액.
        BigDecimal amount,

        // 업무 반영 결과. 예매 취소는 실제 자금 반환을 뜻하지 않는다.
        Outcome outcome
) {

    public enum Outcome {
        APPLIED,
        RESERVATION_CANCELLED
    }

    // 모든 가맹점은 수신한 식별자·금액을 되돌려 같은 이벤트의 처리를 확인한다.
    public static BankDepositNotificationResponse of(BankDepositNotification event, Outcome outcome) {

        return new BankDepositNotificationResponse(event.version(), event.eventId(), event.merchantId(),
                event.pgPaymentId(), event.merchantPaymentNo(), event.accountNumber(), event.amount(), outcome);
    }

    // 응답 유실 재전달과 잘못된 다른 거래 응답을 구분한다.
    public boolean matches(BankDepositNotification event) {

        return version == event.version() && event.eventId().equals(eventId) && event.merchantId().equals(merchantId)
                && event.pgPaymentId().equals(pgPaymentId) && event.merchantPaymentNo().equals(merchantPaymentNo)
                && event.accountNumber().equals(accountNumber) && amount != null && event.amount().compareTo(amount) == 0
                && outcome != null;
    }
}
