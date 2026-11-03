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

    // 좌석 캐시 동기화 Outbox 이벤트 데이터를 조회하고 저장하는 레포지토리.
    private final SeatCacheSyncOutboxJpaRepository repository;
    // 좌석 캐시 동기화 Outbox 이벤트 이벤트 발행을 담당하는 도구.
    private final SeatCacheSyncOutboxPublisher publisher;
    // 한 번에 처리할 발행 대기 이벤트 수.
    @Value("${app.seat-cache.sync-outbox.batch-size:100}")
    private int batchSize = 100;

    /**
     * 좌석 캐시 동기화 Outbox 이벤트 처리에서 발행 오류를 처리하면서 이벤트를 발행한다.
     */
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

    /**
     * 좌석 캐시 동기화 Outbox 이벤트 처리에서 발행 대기 이벤트를 재시도한다.
     */
    @Scheduled(fixedDelayString = "${app.seat-cache.sync-outbox.fixed-delay-ms:30000}")
    public void retryPendingEvents() {

        repository.findReadyIds(SeatCacheSyncOutboxStatus.PENDING, LocalDateTime.now(),
                PageRequest.of(0, batchSize)
        ).forEach(this::publishSafely);
    }
}
