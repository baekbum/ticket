package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalResponse;
import dev.bum.payment_gateway_service.dto.gateway.CardNotificationSnapshot;
import dev.bum.payment_gateway_service.exception.FinancialProviderResponseException;
import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GatewayCardNotificationCommands {

    // 통보 워커 임대와 재시도 상태.
    private final GatewayCardNotificationJpaRepository notifications;
    // 원거래를 먼저 잠가 PG 확정 경로와 잠금 순서를 맞춘다.
    private final GatewayPaymentJpaRepository payments;
    // 확정 거절의 실제 승인 보상 이력.
    private final GatewayPaymentHistoryJpaRepository histories;

    /**
     * 통보 행만 잠깐 잠그고 임대 스냅샷을 반환한다. HTTP 중에는 트랜잭션이 없다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CardNotificationSnapshot claim(Long id) {

        return notifications.findForUpdate(id).map(event -> event.claim(LocalDateTime.now())).orElse(null);
    }

    /**
     * 현재 워커의 수락 결과 또는 금융 보상·PG 취소·통보 완료를 함께 확정한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(CardNotificationSnapshot snapshot, FinancialCardApprovalResponse cancelled) {

        GatewayPayment payment = payments.findOwnedForUpdate(snapshot.merchantId(), snapshot.pgPaymentId())
                .orElseThrow();
        GatewayCardNotification event = notifications.findForUpdate(snapshot.notificationId()).orElseThrow();

        if (!event.owns(snapshot.attempt())) {
            return;
        }

        if (cancelled != null) {
            if (cancelled.status() != CardPaymentHistoryStatus.CANCELLED
                    || !snapshot.pgPaymentId().equals(cancelled.pgPaymentId())
                    || !snapshot.transactionId().equals(cancelled.transactionId())
                    || cancelled.amount() == null || snapshot.amount().compareTo(cancelled.amount()) != 0
                    || snapshot.cardCompany() != cancelled.cardCompany()
                    || !snapshot.maskedCardNumber().equals(cancelled.maskedCardNumber())) {
                throw new FinancialProviderResponseException();
            }

            if (payment.getStatus() == GatewayPaymentStatus.PAID) {
                payment.cancelCardApproval();
                histories.saveAndFlush(GatewayPaymentHistory.cardApprovalCancelled(payment, snapshot.transactionId()));
            } else if (payment.getStatus() != GatewayPaymentStatus.CANCELLED) {
                throw new IllegalStateException("승인 보상을 반영할 수 없는 PG 상태입니다.");
            }
        }

        event.delivered(cancelled == null ? "ACCEPTED" : "REJECTED");
        notifications.saveAndFlush(event);
    }

    /**
     * 실패한 통보만 다시 대기시킨다. 이전 임대의 늦은 실패는 완료 결과를 덮어쓰지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(CardNotificationSnapshot snapshot, String errorType) {

        notifications.findForUpdate(snapshot.notificationId()).filter(event -> event.owns(snapshot.attempt()))
                .ifPresent(event -> event.failed(errorType));
    }
}
