package dev.bum.ticket_service.kafka.reservation;

import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.ticket_service.service.reservation.reservation.ReservationService;
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
    public void consumeInsert(InsertReservationRequest info) {
        log.info("Reservation message received: [eventId: {}, userId: {}, seatSize: {}]",
                info.getEventId(), info.getUserId(), info.getSeats().size());

        try {
            service.createReservationFromQueue(info);
            log.info("Reservation DB sync completed: [eventId: {}, userId: {}]",
                    info.getEventId(), info.getUserId());
        } catch (RuntimeException e) {
            log.error("Reservation message processing failed: [eventId: {}, userId: {}]",
                    info.getEventId(), info.getUserId(), e);
            throw e;
        }
    }
}
