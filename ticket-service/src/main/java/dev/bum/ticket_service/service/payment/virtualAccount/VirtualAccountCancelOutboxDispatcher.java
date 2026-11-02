package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.ticket_service.jpa.payment.virtualAccount.*;
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
public class VirtualAccountCancelOutboxDispatcher {
    private final VirtualAccountCancelOutboxJpaRepository repository;
    private final VirtualAccountCancelOutboxPublisher publisher;
    @Value("${app.virtual-account.cancel-outbox.batch-size:100}")
    private int batchSize = 100;

    @Scheduled(fixedDelayString = "${app.virtual-account.cancel-outbox.fixed-delay-ms:30000}")
    public void retryPendingEvents() {
        for (Long id : repository.findReadyIds(VirtualAccountCancelOutboxStatus.PENDING,
                LocalDateTime.now(), PageRequest.of(0, batchSize))) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                publisher.deliver(id);
            } catch (RuntimeException e) {
                // 완료 기록 커밋 실패도 PENDING 작업을 보존하여 같은 요청으로 복구한다.
                log.warn("가상계좌 취소 Outbox 처리 실패: outboxId={}, errorType={}", id, e.getClass().getSimpleName());
            }
        }
    }
}
