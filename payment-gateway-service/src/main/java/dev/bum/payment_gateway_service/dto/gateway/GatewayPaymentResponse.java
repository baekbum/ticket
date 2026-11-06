package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPayment;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayPaymentResponse(
    // PG가 yyyyMMdd + UUID로 생성한 문자열 거래 식별자. 기존 거래의 36자 UUID도 유지한다.
    String pgPaymentId,

        // 인증된 거래 소유 가맹점의 숫자 코드.
        Long merchantId,

        // 호환되는 가맹점 paymentNo.
        String paymentNo,

        // 가맹점 범위의 예매자.
        String buyerReference,

        // 저장된 결제수단.
        PaymentMethod paymentMethod,

        // 저장된 카드사.
        CardCompany cardCompany,

        // 저장된 은행.
        BankCompany bankCompany,

        // 최초 금액.
        BigDecimal amount,

        // 실제 환불 누적액.
        BigDecimal refundedAmount,

        // 금융 처리에 따른 PG 상태.
        GatewayPaymentStatus status,

        // 현재 수단의 기한.
        LocalDateTime expiresAt,

        // 최초 저장 시각.
        LocalDateTime createdAt
) {

    // 영속 엔티티나 인증 설정을 응답에 노출하지 않는다.
    public static GatewayPaymentResponse from(GatewayPayment payment) {

        return new GatewayPaymentResponse(payment.getPgPaymentId(), payment.getMerchant().getMerchantId(),
                payment.getMerchantPaymentNo(), payment.getBuyerReference(), payment.getPaymentMethod(),
                payment.getCardCompany(), payment.getBankCompany(), payment.getAmount(), payment.getRefundedAmount(),
                payment.getStatus(), payment.getExpiresAt(), payment.getCreatedAt());
    }
}
