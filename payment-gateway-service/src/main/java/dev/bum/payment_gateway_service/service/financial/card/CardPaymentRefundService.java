package dev.bum.payment_gateway_service.service.financial.card;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.jpa.card.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class CardPaymentRefundService {

    // 승인 보상·환불·철회는 같은 원승인 행을 먼저 잠근다.
    private final DummyCardPaymentHistoryJpaRepository approvals;

    // 서로 다른 결제의 환불도 같은 카드 사용액을 안전하게 복원한다.
    private final DummyCardJpaRepository cards;

    // 개별 신청의 최초 성공 또는 철회 결과를 보존한다.
    private final CardPaymentRefundJpaRepository refunds;

    // 잠금 대기 이전의 원승인 스냅샷을 최신 커밋 상태로 교체한다.
    private final EntityManager entityManager;

    public PaymentRefundResult refund(CardRefundCommand request) {

        return execute(request, false);
    }

    public PaymentRefundResult withdraw(CardRefundCommand request) {

        return execute(request, true);
    }

    // 원승인 누적 환불·카드 사용액 복원·개별 처리 기록을 하나의 금융 트랜잭션으로 저장한다.
    private PaymentRefundResult execute(CardRefundCommand request, boolean withdraw) {

        request.validate();
        DummyCardPaymentHistory approval = approvals.findByPgPaymentIdForUpdate(request.pgPaymentId())
                .orElseThrow(() -> new GatewayPaymentConflictException("환불할 카드 원승인이 없습니다."));
        entityManager.refresh(approval, LockModeType.PESSIMISTIC_WRITE);
        CardPaymentRefund existing = refunds.findByPgPaymentIdAndRefundRequestId(
                request.pgPaymentId(), request.refundRequestId()).orElse(null);

        if (existing != null) {
            existing.validate(request);

            return existing.result();
        }

        CardPaymentRefund row = CardPaymentRefund.pending(request);

        if (!withdraw) {
            if (approval.getStatus() != CardPaymentHistoryStatus.APPROVED
                    && approval.getStatus() != CardPaymentHistoryStatus.PARTIALLY_REFUNDED) {
                throw new GatewayPaymentConflictException("환불 가능한 카드 승인 상태가 아닙니다.");
            }

            DummyCard card = cards.findByIdForUpdate(approval.getDummyCard().getDummyCardId()).orElseThrow();
            approval.refund(request.amount());
            card.cancelApproval(request.amount());
        }

        row.complete(withdraw ? "WITHDRAWN" : "SUCCEEDED",
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.MICROS));
        refunds.saveAndFlush(row);

        return row.result();
    }
}
