package dev.bum.payment_gateway_service.service.card;

import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.exception.CardPaymentPendingException;
import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** ticket 결제 완료 요청 전에 카드 승인을 커밋해야 하므로 전체 흐름을 하나의 트랜잭션으로 묶지 않는다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayCardPaymentService {
    private final GatewayCardApprovalService approvalService;
    private final GatewayCardSettlementService settlementService;

    public GatewayCardPaymentApproveResponse approve(String userId, GatewayCardPaymentApproveRequest request) {
        GatewayCardPaymentApproveResponse approval = approvalService.approve(userId, request); // pg 서비스 내부의 결제 상태를 바꿈
        CardPaymentHistoryStatus status = settle(request.getPaymentNo()); // ticket 서비스 내부의 결제 상태를 바꿈

        if (status != CardPaymentHistoryStatus.TICKET_PAYMENT_COMPLETED) {
            throw new IllegalArgumentException("예매를 완료할 수 없거나 이미 취소/환불된 결제입니다.");
        }

        approval.setMessage("카드 결제와 티켓 결제 완료 반영이 완료되었습니다.");
        return approval;
    }

    public CardPaymentHistoryStatus settle(String paymentNo) {
        try {
            return settlementService.settle(paymentNo);
        } catch (RuntimeException failure) {
            try {
                settlementService.recordPending(paymentNo);
            } catch (RuntimeException recordFailure) {
                log.warn("카드 재처리 상태 저장 실패: paymentNo={}", paymentNo);
            }
            throw new CardPaymentPendingException(paymentNo, failure);
        }
    }

    public GatewayCardPaymentStatusResponse status(String userId, String paymentNo) {
        return settlementService.status(userId, paymentNo);
    }

    public GatewayCardPaymentRefundResponse refund(GatewayCardPaymentRefundRequest request) {
        return approvalService.refund(request);
    }
}
