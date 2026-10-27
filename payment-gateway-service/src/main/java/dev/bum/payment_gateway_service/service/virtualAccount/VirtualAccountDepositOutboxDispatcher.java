package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountDepositOutboxJpaRepository;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountDepositOutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountDepositOutboxDispatcher {
    private final VirtualAccountDepositOutboxJpaRepository repository;
    private final VirtualAccountDepositOutboxPublisher publisher;

    @Value("${app.virtual-account.deposit-outbox.batch-size:100}")
    private int batchSize = 100;

    public void deliverSafely(Long outboxId) {
        if (Thread.currentThread().isInterrupted()) return;
        try {
            publisher.deliver(outboxId);
        } catch (RuntimeException e) {
            // 입금은 이미 커밋됐다. 완료 기록 저장 실패도 입금 요청 실패로 바꾸지 않는다.
            log.warn("가상계좌 입금 Outbox 처리 실패: outboxId={}, errorType={}",
                    outboxId, e.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${app.virtual-account.deposit-outbox.fixed-delay-ms:30000}")
    public void retryPendingEvents() {
        repository.findReadyIds(VirtualAccountDepositOutboxStatus.PENDING, LocalDateTime.now(),
                PageRequest.of(0, batchSize)
        ).forEach(this::deliverSafely);
    }
}
