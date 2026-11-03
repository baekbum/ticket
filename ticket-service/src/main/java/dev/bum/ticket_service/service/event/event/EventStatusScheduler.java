package dev.bum.ticket_service.service.event.event;

import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import dev.bum.ticket_service.service.event.policy.EventPolicyMaintenance;
import org.springframework.data.domain.PageRequest;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventStatusScheduler {

    // 공연 데이터를 조회하고 저장하는 레포지토리.
    private final EventJpaRepository eventJpaRepository;
    // 공연 상태와 대기열 정책을 보강하는 도구.
    private final EventPolicyMaintenance maintenance;

    /**
     * 현재 시각에 따라 공연 판매 상태를 갱신한다.
     */
    @Scheduled(cron = "${event.status.scheduler.cron:0 0 0 * * *}")
    public void updateEventStatuses() {

        long cursor = 0;
        while (true) {
            List<Long> ids = eventJpaRepository.findIdsAfter(cursor, PageRequest.of(0, 100));
            for (Long id : ids) maintenance.seedAndReconcile(id);
            if (ids.size() < 100) return;
            cursor = ids.getLast();
        }
    }
}
