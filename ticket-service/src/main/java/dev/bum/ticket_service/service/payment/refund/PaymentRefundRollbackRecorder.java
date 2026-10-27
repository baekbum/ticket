package dev.bum.ticket_service.service.payment.refund;

import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** A가 롤백되어 잠금이 해제된 뒤, 확인된 PG 결과와 실패를 새 트랜잭션에 남긴다. */
@Service
@RequiredArgsConstructor
public class PaymentRefundRollbackRecorder {
    private final ReservationJpaRepository reservations;
    private final PaymentRefundProcessJpaRepository processes;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(PaymentRefundRollbackRecord record) {
        // 다음 취소 요청과도 동일한 잠금 순서를 지켜, 더 최근 작업의 결과를 덮어쓰지 않는다.
        if (reservations.findByIdForUpdate(record.reservationId()).isEmpty()) return;
        PaymentRefundProcess process = processes.findById(record.processId()).orElse(null);
        if (process == null || process.getStatus() == PaymentRefundProcessStatus.LOCAL_SUCCEEDED
                || process.getStatus() == PaymentRefundProcessStatus.SUPERSEDED) return;

        PaymentRefundProcessStatus observed = record.observedStatus();
        if (observed == PaymentRefundProcessStatus.SUPERSEDED) {
            // PG의 실행 차단은 A의 롤백과 무관하게 확정되어 있다.
            process.superseded();
        } else if (observed == PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
                || observed == PaymentRefundProcessStatus.LOCAL_SUCCEEDED
                || observed == PaymentRefundProcessStatus.LOCAL_FAILED) {
            // 확인된 PG 성공은 실패 응답으로 되돌리지 않고 로컬 복구 대상으로 남긴다.
            process.gatewaySucceeded();
            process.localFailed(record.failureReason());
        } else if (observed == PaymentRefundProcessStatus.GATEWAY_FAILED
                && Objects.equals(process.getUpdatedAt(), record.expectedUpdatedAt())
                && process.getStatus() != PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
                && process.getStatus() != PaymentRefundProcessStatus.LOCAL_FAILED) {
            process.gatewayFailed(record.failureReason());
        }
    }
}
