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

    private final VirtualAccountPaymentLockService paymentLocks;
    private final VirtualAccountReconciliationValidator validator;
    private final PaymentCompletionService paymentCompletionService;

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
