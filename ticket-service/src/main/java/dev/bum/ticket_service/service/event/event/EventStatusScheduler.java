package dev.bum.ticket_service.service.event.event;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventStatusScheduler {

    private static final List<EventStatus> CLOSE_TARGET_STATUSES = List.of(
            EventStatus.ON_SALE,
            EventStatus.SALE_ENDED,
            EventStatus.SOLD_OUT
    );

    private final EventJpaRepository eventJpaRepository;

    @Scheduled(cron = "${event.status.scheduler.cron:0 0 0 * * *}")
    @Transactional
    public void updateEventStatuses() {
        LocalDateTime now = LocalDateTime.now();

        int closedCount = eventJpaRepository.closeEventsAfterEventDateTime(
                CLOSE_TARGET_STATUSES,
                EventStatus.CLOSED,
                now
        );
        int saleEndedCount = eventJpaRepository.endSalesAfterSaleEndAt(
                EventStatus.ON_SALE,
                EventStatus.SALE_ENDED,
                now
        );

        if (closedCount > 0 || saleEndedCount > 0) {
            log.info("[EVENT][STATUS] closed={}, saleEnded={}, now={}", closedCount, saleEndedCount, now);
        }
    }
}
