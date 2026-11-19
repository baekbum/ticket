package dev.bum.common.service.paymentgateway.notification;

import java.math.BigDecimal;

public record BankCancellationNotificationResponse(
        // 가맹점이 커밋한 취소 결과 이벤트.
        String eventId,

        // 수신 가맹점.
        Long merchantId,

        // 수신 PG 거래.
        String pgPaymentId,

        // 수신 가맹점 결제번호.
        String merchantPaymentNo,

        // 수신 취소 요청 금액.
        BigDecimal amount,

        // 은행의 업무 결과를 그대로 확인한다. REJECTED도 정상 수신할 수 있다.
        String outcome
) {

    // 가맹점의 결과 수신 커밋을 확인한다.
    public static BankCancellationNotificationResponse of(BankCancellationNotification event) {

        return new BankCancellationNotificationResponse(event.eventId(), event.merchantId(), event.pgPaymentId(),
                event.merchantPaymentNo(), event.amount(), event.outcome());
    }

    // 다른 거래나 다른 업무 결과 응답은 전달 완료로 인정하지 않는다.
    public boolean matches(BankCancellationNotification event) {

        return event.eventId().equals(eventId) && event.merchantId().equals(merchantId)
                && event.pgPaymentId().equals(pgPaymentId) && event.merchantPaymentNo().equals(merchantPaymentNo)
                && amount != null && event.amount().compareTo(amount) == 0 && event.outcome().equals(outcome);
    }
}
