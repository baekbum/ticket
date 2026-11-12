package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.ticket_service.feign.paymentgateway.GatewayPaymentPrepareRequest;

public record GatewayAccountIssuePreparation(
        // 최초 로컬 트랜잭션에 고정한 PG 준비 조건.
        GatewayPaymentPrepareRequest request,

        // 준비 응답을 반영한 이후에만 값이 존재한다.
        String pgPaymentId,

        // 발급 응답 반영까지 완료했으면 HTTP 없이 이 결과를 반환한다.
        boolean pending,

        // 트랜잭션 밖으로 엔티티를 노출하지 않는 로컬 결제 결과.
        PaymentResponse payment
) {
}
