package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.service.payment.payment.PaymentCompletionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VirtualAccountReconciliationCompletionService {

    // 가상계좌 결제 잠금 업무 처리를 담당하는 서비스.
    private final VirtualAccountPaymentLockService paymentLocks;
    // 가상계좌 정합성 대조 요청과 상태를 검증하는 도구.
    private final VirtualAccountReconciliationValidator validator;
    // 결제 완료 업무 처리를 담당하는 서비스.
    private final PaymentCompletionService paymentCompletionService;

    /**
     * 가상계좌 처리에서 완료 상태로 변경하고 결과를 반영한다.
     */
    @Transactional
    public PaymentResponse complete(String paymentNo, GatewayVirtualAccountStatusResponse status) {

        Payment payment = paymentLocks.lock(paymentNo);
        String problem = validator.problem(payment, status);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        return paymentCompletionService.completeDeposit(payment, status.depositedAt(), status.depositorName());
    }
}
