package dev.bum.ticket_service.service.payment.payment;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentFailRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentSettlementResponse;
import dev.bum.common.service.ticket.payment.dto.CardPaymentValidationRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.service.payment.card.CardPaymentService;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentService;

import io.micrometer.observation.annotation.Observed;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    // 카드 결제 업무 처리를 담당하는 서비스.
    private final CardPaymentService cardPaymentService;
    // 가상계좌 결제 업무 처리를 담당하는 서비스.
    private final VirtualAccountPaymentService virtualAccountPaymentService;

    /**
     * payment-gateway의 카드 승인 결과를 검증하고 결제 완료 또는 승인 거절 상태로 정산한다.
     */
    public CardPaymentSettlementResponse settleCardFromGateway(CardPaymentCompleteRequest request) {

        return cardPaymentService.settleFromGateway(request);
    }

    // 신규 PG 통보는 저장된 결제 ID를 함께 확인한다.
    public CardPaymentSettlementResponse settleCardFromGateway(CardPaymentCompleteRequest request, String pgPaymentId) {

        return cardPaymentService.settleFromGateway(request, pgPaymentId);
    }

    /**
     * payment-gateway에서 카드 승인에 성공한 결제를 ticket-service 결제 완료 상태로 반영한다.
     */
    @AuditLog(action = "CARD_PAYMENT_COMPLETE_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.complete-card-from-gateway", contextualName = "ticket payment complete card from gateway")
    public PaymentResponse completeCardFromGateway(CardPaymentCompleteRequest request) {

        return cardPaymentService.completeFromGateway(request);
    }

    /**
     * 공통 PG 통보의 예매 소유자를 확인하고 입금 멱등 처리와 취소 예매 규칙을 적용한다.
     */
    public PaymentResponse completeVirtualAccountDepositFromGateway(
            VirtualAccountDepositCompleteRequest request,
            String buyerReference
    ) {

        return virtualAccountPaymentService.completeDepositFromGateway(request, buyerReference);
    }

    /**
     * payment-gateway가 카드 승인을 요청하기 전에 결제 소유자, 결제수단, 상태 및 만료 여부를 검증한다.
     */
    public PaymentResponse validateCardBeforeApproval(CardPaymentValidationRequest request) {

        return cardPaymentService.validateBeforeApproval(request);
    }

    /**
     * payment-gateway에서 카드 승인 실패 또는 승인 취소가 발생한 결제를 실패 상태로 반영한다.
     */
    @AuditLog(action = "CARD_PAYMENT_FAIL_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.fail-card-from-gateway", contextualName = "ticket payment fail card from gateway")
    public PaymentResponse failCardFromGateway(CardPaymentFailRequest request) {

        return cardPaymentService.failFromGateway(request);
    }
}
