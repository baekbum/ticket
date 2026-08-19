package dev.bum.ticket_service.service.payment;

import dev.bum.common.kafka.payment.VirtualAccountDepositCompletedEvent;
import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentFailRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssuedRequest;
import dev.bum.ticket_service.audit.AuditLog;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    private final CardPaymentService cardPaymentService;
    private final VirtualAccountPaymentService virtualAccountPaymentService;

    @AuditLog(action = "CARD_PAYMENT_COMPLETE_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.complete-card-from-gateway", contextualName = "ticket payment complete card from gateway")
    public PaymentResponse completeCardFromGateway(CardPaymentCompleteRequest request) {
        return cardPaymentService.completeFromGateway(request);
    }

    @AuditLog(action = "CARD_PAYMENT_FAIL_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.fail-card-from-gateway", contextualName = "ticket payment fail card from gateway")
    public PaymentResponse failCardFromGateway(CardPaymentFailRequest request) {
        return cardPaymentService.failFromGateway(request);
    }

    @AuditLog(action = "VIRTUAL_ACCOUNT_ISSUED_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.apply-virtual-account-issued", contextualName = "ticket payment apply virtual account issued")
    public PaymentResponse applyVirtualAccountIssued(VirtualAccountIssuedRequest request) {
        return virtualAccountPaymentService.applyIssuedFromGateway(request);
    }

    @AuditLog(action = "VIRTUAL_ACCOUNT_DEPOSIT_COMPLETED_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.complete-virtual-account-deposit-from-gateway", contextualName = "ticket payment complete virtual account deposit from gateway")
    public PaymentResponse completeVirtualAccountDepositFromGateway(VirtualAccountDepositCompletedEvent event) {
        return virtualAccountPaymentService.completeDepositFromGateway(event);
    }

    @AuditLog(action = "VIRTUAL_ACCOUNT_EXPIRED_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.expire-virtual-account-from-gateway", contextualName = "ticket payment expire virtual account from gateway")
    public PaymentResponse expireVirtualAccountFromGateway(VirtualAccountExpiredEvent event) {
        return virtualAccountPaymentService.expireFromGateway(event);
    }

}
