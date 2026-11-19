package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.paymentgateway.notification.BankCancellationNotification;

public record GatewayBankCancellationNotification(
        // 가맹점에 고정된 내용으로 재전달할 최종 업무 결과.
        BankCancellationNotification notification,

        // 최초 요청 당시의 표준 통보 규격.
        String notificationContract,

        // URL이나 토큰 대신 저장하는 등록 대상 키.
        String notificationTargetKey
) { }
