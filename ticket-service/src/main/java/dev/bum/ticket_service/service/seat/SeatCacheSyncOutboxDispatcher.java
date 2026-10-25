package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutboxJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutboxStatus;
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
public class SeatCacheSyncOutboxDispatcher {
    private final SeatCacheSyncOutboxJpaRepository repository;
    private final SeatCacheSyncOutboxPublisher publisher;

    @Value("${app.seat-cache.sync-outbox.batch-size:100}")
    private int batchSize = 100;

    public void publishSafely(Long outboxId) {
        if (Thread.currentThread().isInterrupted()) return;

        try {
            publisher.publish(outboxId);
        } catch (RuntimeException e) {
            // 원래 DB 작업은 커밋됐다. Outbox 완료 저장 실패도 PENDING 재시도로 복구한다.
            log.warn("좌석 캐시 Outbox 처리 실패: outboxId={}, errorType={}",
                    outboxId, e.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${app.seat-cache.sync-outbox.fixed-delay-ms:30000}")
    public void retryPendingEvents() {
        repository.findReadyIds(SeatCacheSyncOutboxStatus.PENDING, LocalDateTime.now(),
                PageRequest.of(0, batchSize)
        ).forEach(this::publishSafely);
    }
}
