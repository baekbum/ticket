package dev.bum.common.service.paymentgateway.notification;

import java.math.BigDecimal;

public record CardPaymentNotificationResponse(
        // 수신부가 처리한 공통 규격 버전.
        int version,

        // 처리한 원래 이벤트.
        String eventId,

        // 수신 가맹점 코드.
        Long merchantId,

        // 처리한 PG 원거래.
        String pgPaymentId,

        // 처리한 가맹점 결제번호.
        String merchantPaymentNo,

        // 처리한 원승인 식별자.
        String transactionId,

        // 처리한 원승인 금액.
        BigDecimal amount,

        // 단순 HTTP 수신 확인과 구분한 업무 수락 또는 확정 거절.
        Outcome outcome
) {

    // 모든 가맹점이 동일하게 반환하는 업무 처리 결과.
    public enum Outcome {
        // 가맹점이 결제 반영을 커밋했거나 같은 원승인의 기존 결과를 확인했다.
        ACCEPTED,
        // 취소·만료 등으로 원승인을 받을 수 없음을 확정했다.
        REJECTED
    }

    // 수신한 동일 이벤트의 식별 정보와 확정 업무 결과를 반환한다.
    public static CardPaymentNotificationResponse of(CardPaymentNotification request, Outcome outcome) {

        return new CardPaymentNotificationResponse(request.version(), request.eventId(), request.merchantId(),
                request.pgPaymentId(), request.merchantPaymentNo(), request.transactionId(), request.amount(), outcome);
    }

    // 다른 이벤트의 응답이나 잘못된 금액을 통보 완료로 처리하지 않는다.
    public boolean matches(CardPaymentNotification request) {

        return version == request.version() && request.eventId().equals(eventId)
                && request.merchantId().equals(merchantId) && request.pgPaymentId().equals(pgPaymentId)
                && request.merchantPaymentNo().equals(merchantPaymentNo)
                && request.transactionId().equals(transactionId)
                && amount != null && amount.compareTo(request.amount()) == 0 && outcome != null;
    }
}
