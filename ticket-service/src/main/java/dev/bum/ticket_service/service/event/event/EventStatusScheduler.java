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

    private final EventJpaRepository eventJpaRepository;
    private final EventPolicyMaintenance maintenance;

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
