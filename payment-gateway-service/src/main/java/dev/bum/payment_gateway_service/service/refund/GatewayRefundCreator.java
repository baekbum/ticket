package dev.bum.payment_gateway_service.service.refund;

import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefund;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefundJpaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** B에서는 요청 정보만 저장한다. A가 잠근 원래 결제 행은 조회하거나 변경하지 않는다. */
@Service
@RequiredArgsConstructor
public class GatewayRefundCreator {
    private final GatewayRefundJpaRepository refunds;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(GatewayCardPaymentRefundRequest request) {
        return refunds.saveAndFlush(GatewayRefund.requested(request)).getPaymentRefundProcessId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(GatewayVirtualAccountRefundRequest request) {
        return refunds.saveAndFlush(GatewayRefund.requested(request)).getPaymentRefundProcessId();
    }
}
