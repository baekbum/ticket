package dev.bum.common.service.paymentgateway.card;

import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;
import dev.bum.common.service.ticket.payment.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayCardMethodChangeResponse(
        // SWITCHED 또는 APPROVED. 불확실한 금융 결과는 성공 응답으로 만들지 않는다.
        String outcome,

        // 전환 전후에 유지하는 PG 원거래 식별자.
        String pgPaymentId,

        // 원거래 가맹점.
        Long merchantId,

        // 가맹점 결제번호.
        String paymentNo,

        // 원거래 구매자.
        String buyerReference,

        // 현재 저장된 결제수단.
        PaymentMethod paymentMethod,

        // 최초 결제 금액.
        BigDecimal amount,

        // 전환이 확정된 발급 은행. 카드 승인 성공이면 null이다.
        BankCompany bankCompany,

        // 현재 결제수단의 기한.
        LocalDateTime expiresAt,

        // 기존 카드 세션 무효화에 사용하는 버전.
        int methodRevision,

        // 승인 성공 복구 시 기존 Outbox와 같은 원승인 사실. 전환 성공이면 null이다.
        CardPaymentNotification approval
) { }
