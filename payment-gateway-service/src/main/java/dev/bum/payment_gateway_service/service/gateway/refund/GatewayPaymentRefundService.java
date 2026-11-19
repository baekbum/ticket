package dev.bum.payment_gateway_service.service.gateway.refund;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.support.GatewayPaymentIdNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayPaymentRefundService {

    // 요청 본문의 결제수단을 신뢰하지 않고 저장된 원거래로 분기한다.
    private final GatewayPaymentJpaRepository payments;

    private final GatewayBankRefundService bank;

    private final GatewayCardRefundService card;

    public PaymentRefundResult execute(
            AuthenticatedMerchant merchant,
            String pgPaymentId,
            String refundRequestId,
            GatewayPaymentRefundRequest request,
            boolean withdraw
    ) {

        if (merchant == null || merchant.merchantId() == null
                || (request.merchantId() != null && !request.merchantId().equals(merchant.merchantId()))) {
            throw new AccessDeniedException("환불 가맹점이 일치하지 않습니다.");
        }

        String id = GatewayPaymentIdNormalizer.normalize(pgPaymentId);
        GatewayPayment payment = payments.findByPgPaymentIdAndMerchantMerchantId(id, merchant.merchantId())
                .orElseThrow(GatewayPaymentNotFoundException::new);

        if (!payment.getBuyerReference().equals(request.buyerReference())) {
            throw new GatewayPaymentConflictException("환불 원거래의 소유자가 일치하지 않습니다.");
        }

        if (payment.getPaymentMethod() == PaymentMethod.CREDIT_CARD) {
            return card.execute(merchant, id, refundRequestId, request, withdraw);
        }

        if (payment.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            return PaymentRefundResult.from(bank.execute(merchant, id, refundRequestId, request.bankRequest(), withdraw));
        }

        throw new GatewayPaymentConflictException("환불을 지원하지 않는 결제수단입니다.");
    }
}
