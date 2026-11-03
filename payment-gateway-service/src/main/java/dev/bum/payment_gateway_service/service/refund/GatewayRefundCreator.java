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

    // 게이트웨이 환불 데이터를 조회하고 저장하는 레포지토리.
    private final GatewayRefundJpaRepository refunds;

    /**
     * 환불을 생성한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(GatewayCardPaymentRefundRequest request) {

        return refunds.saveAndFlush(GatewayRefund.requested(request)).getPaymentRefundProcessId();
    }

    /**
     * 환불을 생성한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(GatewayVirtualAccountRefundRequest request) {

        return refunds.saveAndFlush(GatewayRefund.requested(request)).getPaymentRefundProcessId();
    }
}
