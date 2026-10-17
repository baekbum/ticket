package dev.bum.payment_gateway_service.service.refund;

import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse.Outcome;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse;
import dev.bum.payment_gateway_service.jpa.card.DummyCardPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefund;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefundJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayRefundResolutionService {
    private final GatewayRefundJpaRepository refunds;
    private final DummyCardPaymentHistoryJpaRepository cardPayments;
    private final DummyVirtualAccountJpaRepository virtualAccounts;

    @Transactional
    public GatewayRefundResolutionResponse resolve(GatewayRefundResolutionRequest request) {
        // 환불 실행과 같은 결제 행을 잠가, 결과 확인·종료와 늦게 도착한 환불 요청을 직렬화한다.
        switch (request.method()) {
            case CREDIT_CARD -> cardPayments.findByPaymentNoForUpdate(request.paymentNo())
                    .orElseThrow(() -> new IllegalArgumentException("카드 승인 이력을 찾을 수 없습니다."));
            case BANK_TRANSFER -> virtualAccounts.findByPaymentNoForUpdate(request.paymentNo())
                    .orElseThrow(() -> new IllegalArgumentException("가상계좌 정보를 찾을 수 없습니다."));
            default -> throw new IllegalArgumentException("지원하지 않는 환불 수단입니다.");
        }
        GatewayRefund existing = refunds.findById(request.paymentRefundProcessId()).orElse(null);
        Outcome outcome;
        if (existing != null) {
            existing.validateResolution(request);
            outcome = existing.isClosed() ? Outcome.CLOSED : Outcome.SUCCEEDED;
        } else if (request.closeIfUnexecuted()) {
            refunds.saveAndFlush(GatewayRefund.closed(request));
            outcome = Outcome.CLOSED;
        } else {
            outcome = Outcome.NOT_FOUND;
        }
        return GatewayRefundResolutionResponse.builder()
                .paymentRefundProcessId(request.paymentRefundProcessId())
                .paymentNo(request.paymentNo())
                .outcome(outcome)
                .build();
    }
}
