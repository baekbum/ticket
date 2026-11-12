package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.ticket_service.jpa.payment.virtualAccount.*;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class VirtualAccountCancelOutboxCommands {

    // 취소 요청의 불변 스냅샷과 전달 상태를 저장한다.
    private final VirtualAccountCancelOutboxJpaRepository repository;
    // 취소·입금·발급 결과와 같은 예약 → 결제 순서로 잠근다.
    private final VirtualAccountPaymentLockService locks;
    // 잠금을 기다리는 동안 바뀐 Outbox 상태를 다시 읽는다.
    private final EntityManager entityManager;

    /**
     * 발급 중 취소된 계좌의 연결을 복구한 뒤 외부 호출에 필요한 스냅샷만 반환한다.
     */
    public VirtualAccountCancelOutbox claimDelivery(Long id) {

        VirtualAccountCancelOutbox candidate = repository.findById(id).orElse(null);

        if (candidate == null) {
            return null;
        }

        if (candidate.getBankCompany() != null) {
            Payment payment = locks.lock(candidate.getPaymentNo());
            VirtualAccountCancelOutbox locked = repository.findByIdForUpdate(id).orElseThrow();
            entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
            locked.bindGateway(payment);
            candidate = locked;
        }

        if (candidate.getBankCompany() == null) {
            candidate = repository.findByIdForUpdate(id).orElseThrow();
            entityManager.refresh(candidate, LockModeType.PESSIMISTIC_WRITE);
        }

        if (!candidate.claim(LocalDateTime.now())) {
            return null;
        }

        return candidate;
    }

    /**
     * 취소용 원거래 복구 후에도 같은 시도가 선점을 보유하는지 확인하고 갱신된 계좌 스냅샷을 읽는다.
     */
    @Transactional(readOnly = true)
    public VirtualAccountCancelOutbox findClaimedDelivery(Long id, long attempt) {

        return repository.findById(id)
                .filter(outbox -> outbox.getStatus() == VirtualAccountCancelOutboxStatus.PENDING
                        && outbox.getDeliveryAttempt() == attempt)
                .orElse(null);
    }

    /**
     * 202 접수 또는 확정 결과를 저장한다. 최종 통보가 먼저 도착했으면 그 결과를 유지한다.
     */
    public void delivered(Long id, long attempt, String result, String reason) {

        VirtualAccountCancelOutbox outbox = repository.findByIdForUpdate(id).orElseThrow();

        if (outbox.getDeliveryAttempt() != attempt) {
            return;
        }

        if (result != null) {
            outbox.gatewayResult(result, reason);
        }

        outbox.delivered(LocalDateTime.now());
    }

    /**
     * 전송 실패를 저장한다. 이미 수신한 최종 성공·거절을 전송 실패로 되돌리지 않는다.
     */
    public void failed(Long id, long attempt, String errorType, LocalDateTime nextAttemptAt) {

        repository.findByIdForUpdate(id).ifPresent(outbox -> {
            if (outbox.getDeliveryAttempt() == attempt) {
                outbox.failed(errorType, nextAttemptAt);
            }
        });
    }
}
