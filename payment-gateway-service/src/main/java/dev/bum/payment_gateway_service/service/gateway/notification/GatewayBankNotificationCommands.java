package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.gateway.BankNotificationSnapshot;
import dev.bum.payment_gateway_service.exception.FinancialProviderResponseException;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GatewayBankNotificationCommands {

    // PG→가맹점 입금 통보의 짧은 임대/결과 트랜잭션.
    private final GatewayBankNotificationJpaRepository notifications;
    // 입금 확정과 같은 순서로 PG 원거래를 먼저 잠근다.
    private final GatewayPaymentJpaRepository payments;
    // 실제 반환 없이 취소 예매 상태로 수렴한 이력.
    private final GatewayPaymentHistoryJpaRepository histories;

    /**
     * HTTP 호출 전에 임대만 커밋하고 최초 통보 스냅샷을 반환한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankNotificationSnapshot claim(Long id) {

        return notifications.findForUpdate(id).map(event -> event.claim(LocalDateTime.now())).orElse(null);
    }

    /**
     * 수락 또는 은행 계좌 정리 결과를 PG 상태·이력·Outbox 완료와 함께 반영한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(BankNotificationSnapshot snapshot, BankVirtualAccountDepositResponse cancelled) {

        GatewayPayment payment = payments.findOwnedForUpdate(snapshot.merchantId(), snapshot.pgPaymentId()).orElseThrow();
        GatewayBankNotification event = notifications.findForUpdate(snapshot.notificationId()).orElseThrow();

        if (!event.owns(snapshot.attempt())) {
            return;
        }

        if (cancelled != null) {
            if (cancelled.status() != VirtualAccountPaymentStatus.CANCELLED
                    || !snapshot.pgPaymentId().equals(cancelled.pgPaymentId())
                    || !snapshot.accountNumber().equals(cancelled.accountNumber())
                    || snapshot.bankCompany() != cancelled.bankCompany() || cancelled.amount() == null
                    || snapshot.amount().compareTo(cancelled.amount()) != 0
                    || !snapshot.depositorName().equals(cancelled.depositorName())
                    || !snapshot.depositedAt().equals(cancelled.depositedAt())) {
                throw new FinancialProviderResponseException();
            }

            if (payment.getStatus() != GatewayPaymentStatus.CANCELLED) {
                payment.cancelDepositedPayment();
                histories.saveAndFlush(GatewayPaymentHistory.depositedPaymentCancelled(payment));
            }
        }

        event.delivered(cancelled == null ? "APPLIED" : "RESERVATION_CANCELLED");
        notifications.saveAndFlush(event);
    }

    /**
     * 금융 성공은 유지하고 현재 임대의 실패만 동일 이벤트로 재시도한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(BankNotificationSnapshot snapshot, String errorType) {

        notifications.findForUpdate(snapshot.notificationId()).filter(event -> event.owns(snapshot.attempt()))
                .ifPresent(event -> event.failed(errorType));
    }
}
