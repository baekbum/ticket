package dev.bum.audit_service.kafka;

import dev.bum.audit_service.ticketing.TicketingDetectionLogEntity;
import dev.bum.audit_service.ticketing.TicketingDetectionLogInsertRepository;
import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketingDetectionLogConsumer {

    private final TicketingDetectionLogInsertRepository repository;

    @KafkaListener(
            topics = "${topic.ticketing.detection.log.name}",
            groupId = "${app.kafka.ticketing-detection-log.group-id}",
            containerFactory = "ticketingDetectionLogKafkaListenerContainerFactory",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void consume(TicketingDetectionLogEvent event) {

        repository.insertIfAbsent(TicketingDetectionLogEntity.from(event));
    }
}
