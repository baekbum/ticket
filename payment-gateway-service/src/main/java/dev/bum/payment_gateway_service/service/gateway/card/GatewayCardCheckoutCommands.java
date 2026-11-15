package dev.bum.payment_gateway_service.service.gateway.card;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.security.card.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatewayCardCheckoutCommands {

    private final GatewayPaymentJpaRepository payments;

    private final GatewayCardSessionToken tokens;

    // 인증된 가맹점이 소유한 카드 거래의 세션만 발급한다. 이 메서드는 금융 HTTP를 호출하지 않는다.
    public GatewayCardSessionResponse issue(
            AuthenticatedMerchant merchant,
            String id,
            GatewayCardSessionRequest request
    ) {

        if (merchant == null || merchant.merchantId() == null) {
            throw new AccessDeniedException("가맹점 인증이 필요합니다.");
        }

        GatewayPayment payment = payments.findByPgPaymentIdAndMerchantMerchantId(id, merchant.merchantId())
                .orElseThrow(GatewayPaymentNotFoundException::new);

        if (payment.getPaymentMethod() != PaymentMethod.CREDIT_CARD
                || !payment.getBuyerReference().equals(request.buyerReference())) {
            throw new GatewayPaymentConflictException("카드 결제 세션의 원거래가 일치하지 않습니다.");
        }

        return tokens.issue(payment, request.approvalAllowed());
    }

    // 서명된 merchant·buyer·수단·revision을 현재 원거래와 대조한다.
    public AuthenticatedMerchant validate(GatewayCardSessionClaims session) {

        GatewayPayment payment = payments.findByPgPaymentIdAndMerchantMerchantId(session.pgPaymentId(), session.merchantId())
                .orElseThrow(GatewayPaymentNotFoundException::new);

        if (payment.getPaymentMethod() != PaymentMethod.CREDIT_CARD
                || !payment.getBuyerReference().equals(session.buyerReference())
                || payment.getMethodRevision() != session.methodRevision()) {
            throw new AccessDeniedException("결제 세션의 원거래 조건이 변경되었습니다.");
        }

        return new AuthenticatedMerchant(session.merchantId());
    }
}
