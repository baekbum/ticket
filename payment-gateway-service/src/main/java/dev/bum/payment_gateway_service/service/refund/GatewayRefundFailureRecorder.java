package dev.bum.payment_gateway_service.service.refund;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.jpa.card.DummyCardPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefund;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefundJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 작업 트랜잭션이 롤백되어 결제 잠금이 해제된 뒤 실패 상태를 보존한다. */
@Service
@RequiredArgsConstructor
public class GatewayRefundFailureRecorder {
    private final GatewayRefundJpaRepository refunds;
    private final DummyCardPaymentHistoryJpaRepository cardPayments;
    private final DummyVirtualAccountJpaRepository virtualAccounts;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long processId, String paymentNo, PaymentMethod method, Long expectedVersion, String reason) {
        // 환불·종료와 동일한 원래 결제 행을 잠그고 최신 상태를 확인한다.
        boolean paymentExists = switch (method) {
            case CREDIT_CARD -> cardPayments.findByPaymentNoForUpdate(paymentNo).isPresent();
            case BANK_TRANSFER -> virtualAccounts.findByPaymentNoForUpdate(paymentNo).isPresent();
            default -> false;
        };
        if (!paymentExists) return;

        GatewayRefund refund = refunds.findById(processId).orElse(null);
        if (refund == null || !Objects.equals(refund.getPaymentNo(), paymentNo) || refund.getMethod() != method
                || refund.isSucceeded() || refund.isClosed()
                || !Objects.equals(refund.getVersion(), expectedVersion)) return;

        // 먼저 끝난 재시도나 종료 요청의 결과는 과거 실패로 덮어쓰지 않는다.
        refund.failed(reason);
    }
}
