package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;

public record CardNotificationSnapshot(
        // 통보 행 ID.
        Long notificationId,

        // 재전달에도 바뀌지 않는 이벤트 ID.
        String eventId,

        // PG 거래 ID.
        String pgPaymentId,

        // 가맹점 코드.
        Long merchantId,

        // 가맹점 결제번호.
        String paymentNo,

        // 예매자 참조값.
        String buyerReference,

        // 통보 DTO 필드 구성·응답 규칙의 계약 버전. 현재는 V1 고정이며 클래스명이 아니다.
        String notificationContract,

        // 가맹점 ID와 함께 DB의 통보 대상을 조회하는 키. 재시도 때도 같은 키를 사용한다.
        String targetKey,

        // 원승인 번호.
        String transactionId,

        // 승인 카드사.
        CardCompany cardCompany,

        // 마스킹 카드번호.
        String maskedCardNumber,

        // 원승인 금액.
        BigDecimal amount,

        // 금융 승인 시각.
        LocalDateTime approvedAt,

        // 이번 전달 임대의 세대.
        int attempt
) {

    // 재시도/가맹점과 무관하게 동일 필드와 같은 원승인 사실을 전달한다.
    public CardPaymentNotification toNotification() {

        return new CardPaymentNotification(1, eventId, merchantId, pgPaymentId, paymentNo, buyerReference,
                PaymentMethod.CREDIT_CARD, PaymentStatus.PAID, amount, transactionId, cardCompany,
                maskedCardNumber, approvedAt);
    }
}
