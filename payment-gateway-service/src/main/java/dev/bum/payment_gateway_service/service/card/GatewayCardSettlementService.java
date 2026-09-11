package dev.bum.payment_gateway_service.service.card;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentSettlementResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentStatusResponse;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.card.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayCardSettlementService {
    private final DummyCardPaymentHistoryJpaRepository histories;
    private final DummyCardJpaRepository cards;
    private final TicketPaymentClient ticketClient;

    @Transactional
    public CardPaymentHistoryStatus settle(String paymentNo) {
        DummyCardPaymentHistory history = histories.findByPaymentNoForUpdate(paymentNo).orElseThrow();
        if (!pending(history)) {
            return history.getStatus();
        }

        // ticket 서비스를 호출하여 해당 결제 건의 상태를 완료로 변경하는 작업
        CardPaymentSettlementResponse result = ticketClient.settleCardPayment(CardPaymentCompleteRequest.builder()
                .paymentNo(history.getPaymentNo())
                .userId(history.getUserId())
                .amount(history.getAmount())
                .transactionId(history.getTransactionId())
                .cardCompany(history.getCardCompany())
                .maskedCardNumber(history.getMaskedCardNumber())
                .build());

        if (result == null || result.payment() == null
                || !history.getPaymentNo().equals(result.payment().getPaymentNo())
                || result.payment().getAmount() == null
                || history.getAmount().compareTo(java.math.BigDecimal.valueOf(result.payment().getAmount())) != 0) {
            throw new IllegalStateException("ticket 결제 반영 응답을 확인할 수 없습니다.");
        }

        if (result.outcome() == CardPaymentSettlementResponse.Outcome.COMPLETED
                && history.getTransactionId().equals(result.payment().getCardTransactionId())
                && (result.payment().getStatus() == PaymentStatus.PAID
                    || result.payment().getStatus() == PaymentStatus.REFUNDED
                    || result.payment().getStatus() == PaymentStatus.PARTIALLY_REFUNDED)) {

            // 문제가 없다면 결제 이력의 상태를 결제 완료( 티켓 서비스도 동기화된 상태 )로 변경한다.
            history.completeTicketPayment(null);

        } else if (result.outcome() == CardPaymentSettlementResponse.Outcome.REJECTED
                && (result.payment().getStatus() == PaymentStatus.EXPIRED
                    || result.payment().getStatus() == PaymentStatus.CANCELLED
                    || result.payment().getStatus() == PaymentStatus.FAILED)) {

            DummyCard card = cards.findByIdForUpdate(history.getDummyCard().getDummyCardId()).orElseThrow();
            card.cancelApproval(history.getAmount());
            history.cancel("ticket 결제 실패: " + result.payment().getStatus());

        } else {
            throw new IllegalStateException("ticket 결제 반영 결과가 불명확합니다.");
        }

        return history.getStatus();
    }

    @Transactional
    public void recordPending(String paymentNo) {
        DummyCardPaymentHistory history = histories.findByPaymentNoForUpdate(paymentNo).orElseThrow();
        if (pending(history)) {
            history.failTicketPayment("결제 완료 결과 확인 대기. 동일 거래번호로 자동 재처리합니다.");
        }
    }

    @Transactional(readOnly = true)
    public GatewayCardPaymentStatusResponse status(String userId, String paymentNo) {
        DummyCardPaymentHistory history = histories.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new IllegalArgumentException("카드 승인 이력을 찾을 수 없습니다."));
        if (userId == null || !userId.equals(history.getUserId())) {
            throw new AccessDeniedException("다른 사용자의 결제입니다.");
        }
        return new GatewayCardPaymentStatusResponse(paymentNo, history.getStatus(), history.getTransactionId());
    }

    private boolean pending(DummyCardPaymentHistory history) {
        return history.getStatus() == CardPaymentHistoryStatus.APPROVED
                || history.getStatus() == CardPaymentHistoryStatus.TICKET_PAYMENT_FAILED;
    }
}
