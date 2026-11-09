package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayCardApprovalResponse(
        // PG 거래 식별자.
        String pgPaymentId,

        // 거래 소유 가맹점.
        Long merchantId,

        // 가맹점 내부 원결제번호.
        String paymentNo,

        // 준비된 예매자.
        String buyerReference,

        // 승인 카드사.
        CardCompany cardCompany,

        // 금융 원승인 식별자.
        String transactionId,

        // 마스킹 카드번호.
        String maskedCardNumber,

        // 원승인 금액.
        BigDecimal amount,

        // 금융 승인 시각.
        LocalDateTime approvedAt,

        // PG 금융 상태. 통보 결과와 구분한다.
        GatewayPaymentStatus pgStatus,

        // 별도로 저장된 가맹점 결과 통보 상태.
        String notificationStatus
) { }
