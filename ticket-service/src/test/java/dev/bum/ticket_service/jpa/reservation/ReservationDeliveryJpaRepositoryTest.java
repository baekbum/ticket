package dev.bum.ticket_service.jpa.reservation;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationDeliveryStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ReservationDeliveryJpaRepositoryTest {

    @Autowired
    private ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;

    @Autowired
    private ReservationJpaRepository reservationJpaRepository;

    @Autowired
    private EventJpaRepository eventJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("배송 스냅샷 저장 후 예약으로 조회한다")
    void save_and_find_by_reservation() {
        Reservation reservation = reservationJpaRepository.save(reservation("order-1", "user01"));
        ReservationDelivery saved = reservationDeliveryJpaRepository.save(new ReservationDelivery(reservation, deliveryRequest()));
        entityManager.flush();
        entityManager.clear();

        Reservation responseReservation = reservationJpaRepository.findById(reservation.getReservationId()).orElseThrow();
        ReservationDelivery response = reservationDeliveryJpaRepository.findByReservation(responseReservation).orElseThrow();

        assertThat(response.getReservationDeliveryId()).isEqualTo(saved.getReservationDeliveryId());
        assertThat(response.getRecipientName()).isEqualTo("receiver");
        assertThat(response.getRecipientPhone()).isEqualTo("010-1234-5678");
        assertThat(response.getZipCode()).isEqualTo("12345");
        assertThat(response.getAddress()).isEqualTo("Seoul Olympic-ro");
        assertThat(response.getDetailAddress()).isEqualTo("101-1001");
        assertThat(response.getDeliveryMessage()).isEqualTo("Leave at door");
        assertThat(response.getStatus()).isEqualTo(ReservationDeliveryStatus.READY);
        assertThat(reservationDeliveryJpaRepository.existsByReservation(responseReservation)).isTrue();
    }

    private Reservation reservation(String orderId, String userId) {
        return Reservation.builder()
                .orderId(orderId)
                .userId(userId)
                .event(eventJpaRepository.save(event()))
                .status(ReservationStatus.PENDING_PAYMENT)
                .reservedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Event event() {
        return Event.builder()
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .totalSeats(100)
                .availableSeats(100)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }

    private ReservationDeliveryRequest deliveryRequest() {
        return ReservationDeliveryRequest.builder()
                .recipientName("receiver")
                .recipientPhone("010-1234-5678")
                .zipCode("12345")
                .address("Seoul Olympic-ro")
                .detailAddress("101-1001")
                .deliveryMessage("Leave at door")
                .build();
    }
}
