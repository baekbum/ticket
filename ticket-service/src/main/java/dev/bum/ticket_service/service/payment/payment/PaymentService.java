package dev.bum.ticket_service.service.payment.payment;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentSettlementResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
import dev.bum.ticket_service.service.payment.card.CardPaymentService;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentService;

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

    // 신규 PG 통보는 저장된 결제 ID를 함께 확인한다.
    public CardPaymentSettlementResponse settleCardFromGateway(CardPaymentCompleteRequest request, String pgPaymentId) {

        return cardPaymentService.settleFromGateway(request, pgPaymentId);
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

}
