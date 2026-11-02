package dev.bum.ticket_service.service.event.policy;

import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.policy.EventPolicyOutbox;
import dev.bum.ticket_service.jpa.event.policy.EventPolicyOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPolicyDispatcher {
    private final EventPolicyOutboxJpaRepository outbox;
    private final EventJpaRepository events;
    private final EventPolicyMaintenance maintenance;
    private final EventPolicyPublisher publisher;
    private long seedCursor;
    private long replayCursor;

    @Scheduled(fixedDelayString = "${app.event-policy.publish-delay-ms:1000}")
    public void publishPending() {
        List<Long> ids = outbox.findReady(LocalDateTime.now(), PageRequest.of(0, 100));

        for (Long id : ids) {
            deliver(id, false);
        }
    }

    /** 일괄 재적재와 Redis 유실 복구. HTTP 요청 경로에서는 Ticket을 조회하지 않는다. */
    @Scheduled(initialDelayString = "${app.event-policy.reconcile-delay-ms:30000}",
            fixedDelayString = "${app.event-policy.reconcile-delay-ms:30000}")
    public void reconcile() {
        List<Long> ids = events.findIdsAfter(seedCursor, PageRequest.of(0, 100));
        for (Long id : ids) maintenance.seedAndReconcile(id);
        seedCursor = ids.size() < 100 ? 0 : ids.getLast();
        List<EventPolicyOutbox> snapshots = outbox.findLatestAfter(replayCursor, PageRequest.of(0, 100));
        for (EventPolicyOutbox row : snapshots) deliver(row.getOutboxId(), true);
        replayCursor = snapshots.size() < 100 ? 0 : snapshots.getLast().getEventId();
    }

    private void deliver(Long id, boolean replay) {
        try {
            publisher.deliver(id, replay);
        } catch (RuntimeException failure) {
            log.warn("공연 정책 전송 재시도 대기: outboxId={}, errorType={}", id, failure.getClass().getSimpleName());
        }
    }
}
