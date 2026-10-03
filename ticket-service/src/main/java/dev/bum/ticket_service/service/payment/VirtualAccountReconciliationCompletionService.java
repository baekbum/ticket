package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VirtualAccountReconciliationCompletionService {

    private final PaymentJpaRepository payments;
    private final VirtualAccountReconciliationValidator validator;
    private final PaymentCompletionService paymentCompletionService;

    @Transactional
    public PaymentResponse complete(String paymentNo, GatewayVirtualAccountStatusResponse status) {
        Payment payment = payments.findByPaymentNoForUpdate(paymentNo)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        String problem = validator.problem(payment, status);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        return paymentCompletionService.completeDeposit(payment, status.depositedAt(), status.depositorName());
    }
}
