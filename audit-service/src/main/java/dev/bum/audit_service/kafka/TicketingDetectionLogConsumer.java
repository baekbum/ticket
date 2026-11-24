package dev.bum.audit_service.kafka;

import dev.bum.audit_service.ticketing.TicketingDetectionLogPersistenceService;
import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketingDetectionLogConsumer {

    private final TicketingDetectionLogPersistenceService persistenceService;

    @KafkaListener(
            topics = "${topic.ticketing.detection.log.name}",
            groupId = "${app.kafka.ticketing-detection-log.group-id}",
            containerFactory = "ticketingDetectionLogKafkaListenerContainerFactory",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void consume(TicketingDetectionLogEvent event) {

        persistenceService.save(event);
    }
}
