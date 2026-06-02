package dev.bum.ticket_service.kafka.reservation;

import dev.bum.ticket_service.service.reservation.ReservationService;
import dev.bum.ticket_service.vo.reservation.InsertReservationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationConsumer {

    private final ReservationService service;

    @Value("${topic.reservation.name}")
    private String reservationTopic;

    @KafkaListener(topics = "${topic.reservation.name}", groupId = "reservation-group")
    public void consumeInsert(InsertReservationInfo info) {
        log.info(">>>> Kafka로부터 메시지 도착: [eventId: {}, userId: {}, seatSize: {}]", info.getEventId(), info.getUserId(), info.getSeats().size());

        try {
            service.createReservationFromQueue(info);
            log.info(">>>> 권한 서비스 DB 동기화 완료");
        } catch (Exception e) {
            log.error(">>>> 데이터 처리 중 에러 발생: {}", e.getMessage());
        }
    }
}
