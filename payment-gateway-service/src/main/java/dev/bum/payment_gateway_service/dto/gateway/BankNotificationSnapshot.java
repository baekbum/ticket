package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.paymentgateway.notification.BankDepositNotification;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankNotificationSnapshot(
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

        // 최초 입금 번호.
        String accountNumber,

        // 입금 은행.
        BankCompany bankCompany,

        // 입금자명.
        String depositorName,

        // 최초 입금 금액.
        BigDecimal amount,

        // 금융 입금 시각.
        LocalDateTime depositedAt,

        // 이번 전달 임대의 세대.
        int attempt
) {

    // 재시도/가맹점과 무관하게 동일 필드와 같은 최초 입금 사실을 전달한다.
    public BankDepositNotification toNotification() {

        return new BankDepositNotification(1, eventId, merchantId, pgPaymentId, paymentNo, buyerReference,
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID, amount, accountNumber, bankCompany,
                depositorName, depositedAt);
    }
}
