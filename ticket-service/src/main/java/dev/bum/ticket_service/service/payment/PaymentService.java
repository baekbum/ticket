package dev.bum.ticket_service.service.payment;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentFailRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
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

    /**
     * payment-gateway에서 카드 승인에 성공한 결제를 ticket-service 결제 완료 상태로 반영한다.
     */
    @AuditLog(action = "CARD_PAYMENT_COMPLETE_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.complete-card-from-gateway", contextualName = "ticket payment complete card from gateway")
    public PaymentResponse completeCardFromGateway(CardPaymentCompleteRequest request) {
        return cardPaymentService.completeFromGateway(request);
    }

    /**
     * payment-gateway에서 카드 승인 실패 또는 승인 취소가 발생한 결제를 실패 상태로 반영한다.
     */
    @AuditLog(action = "CARD_PAYMENT_FAIL_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.fail-card-from-gateway", contextualName = "ticket payment fail card from gateway")
    public PaymentResponse failCardFromGateway(CardPaymentFailRequest request) {
        return cardPaymentService.failFromGateway(request);
    }

    /**
     * payment-gateway에서 발급한 가상계좌 정보를 무통장 결제에 반영하고 입금 대기 상태로 전환한다.
     */
    @AuditLog(action = "VIRTUAL_ACCOUNT_ISSUED_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.apply-virtual-account-issued", contextualName = "ticket payment apply virtual account issued")
    public PaymentResponse applyVirtualAccountIssued(VirtualAccountIssuedRequest request) {
        return virtualAccountPaymentService.applyIssuedFromGateway(request);
    }

    /**
     * payment-gateway에서 확인한 가상계좌 입금 완료 이벤트를 결제 완료 상태로 반영한다.
     */
    @AuditLog(action = "VIRTUAL_ACCOUNT_DEPOSIT_COMPLETED_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.complete-virtual-account-deposit-from-gateway", contextualName = "ticket payment complete virtual account deposit from gateway")
    public PaymentResponse completeVirtualAccountDepositFromGateway(VirtualAccountDepositCompleteRequest request) {
        return virtualAccountPaymentService.completeDepositFromGateway(request);
    }

    /**
     * payment-gateway 만료 스케줄러가 발행한 가상계좌 만료 이벤트를 결제 만료 상태로 반영한다.
     */
    @AuditLog(action = "VIRTUAL_ACCOUNT_EXPIRED_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.expire-virtual-account-from-gateway", contextualName = "ticket payment expire virtual account from gateway")
    public PaymentResponse expireVirtualAccountFromGateway(VirtualAccountExpiredEvent event) {
        return virtualAccountPaymentService.expireFromGateway(event);
    }

}
