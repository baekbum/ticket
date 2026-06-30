package dev.bum.ticket_service.kafka.reservation;

import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationProducer {

    private final KafkaTemplate<Long, InsertReservationRequest> kafkaTemplate;

    @Value("${topic.reservation.name}")
    private String reservationTopic;

    /**
     * Reservation 정보를 카프카 큐에 전송하는 전담 메서드
     */
    public void send(InsertReservationRequest info) {
        kafkaTemplate.send(reservationTopic, info.getEventId(), info)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Kafka 전송 성공: [topic: {}, eventId: {}, userId: {}]", reservationTopic, info.getEventId(), info.getUserId());
                    } else {
                        log.error("Kafka 전송 실패: [topic: {}, eventId: {}, userId: {}]", reservationTopic, info.getEventId(), info.getUserId(), ex);
                    }
                });
    }
}
