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

    // 공연 정책 데이터를 조회하고 저장하는 레포지토리.
    private final EventPolicyOutboxJpaRepository outbox;
    // 공연 데이터를 조회하고 저장하는 레포지토리.
    private final EventJpaRepository events;
    // 공연 상태와 대기열 정책을 보강하는 도구.
    private final EventPolicyMaintenance maintenance;
    // 공연 정책 이벤트 발행을 담당하는 도구.
    private final EventPolicyPublisher publisher;
    // 초기 정책 보강 대상을 순회할 마지막 공연 식별자.
    private long seedCursor;
    // 정책 재발행 대상을 순회할 마지막 공연 식별자.
    private long replayCursor;

    /**
     * 발행 대기 중인 이벤트를 발행한다.
     */
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

    /**
     * 공연 처리에서 후속 처리 대상으로 전달한다.
     */
    private void deliver(Long id, boolean replay) {

        try {
            publisher.deliver(id, replay);
        } catch (RuntimeException failure) {
            log.warn("공연 정책 전송 재시도 대기: outboxId={}, errorType={}", id, failure.getClass().getSimpleName());
        }
    }
}
