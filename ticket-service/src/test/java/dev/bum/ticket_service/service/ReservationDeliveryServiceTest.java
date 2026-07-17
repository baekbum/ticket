package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationDeliveryStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.service.reservation.reservationDelivery.ReservationDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ReservationDeliveryServiceTest {

    @InjectMocks
    private ReservationDeliveryService reservationDeliveryService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;

    @Test
    @DisplayName("관리자는 예약 배송 스냅샷을 등록한다")
    void insert() {
        Reservation reservation = reservation(1L, "user01");
        ReservationDeliveryRequest info = deliveryRequest();
        given(reservationRepository.selectById(1L)).willReturn(reservation);
        given(reservationDeliveryJpaRepository.existsByReservation(reservation)).willReturn(false);
        given(reservationDeliveryJpaRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        ReservationDeliveryResponse response = reservationDeliveryService.insert(1L, info);

        assertThat(response.getReservationId()).isEqualTo(1L);
        assertThat(response.getRecipientName()).isEqualTo("receiver");
        assertThat(response.getStatus()).isEqualTo(ReservationDeliveryStatus.READY);
        then(reservationDeliveryJpaRepository).should().save(any(ReservationDelivery.class));
    }

    @Test
    @DisplayName("사용자는 본인 예약 배송 스냅샷을 등록한다")
    void insert_my_delivery() {
        Reservation reservation = reservation(1L, "user01");
        ReservationDeliveryRequest info = deliveryRequest();
        given(reservationRepository.selectById(1L)).willReturn(reservation);
        given(reservationDeliveryJpaRepository.existsByReservation(reservation)).willReturn(false);
        given(reservationDeliveryJpaRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        ReservationDeliveryResponse response = reservationDeliveryService.insertMyDelivery("user01", 1L, info);

        assertThat(response.getRecipientName()).isEqualTo("receiver");
        then(reservationDeliveryJpaRepository).should().save(any(ReservationDelivery.class));
    }

    @Test
    @DisplayName("다른 사용자 예약 배송 스냅샷은 등록할 수 없다")
    void insert_my_delivery_forbidden() {
        Reservation reservation = reservation(1L, "other-user");
        given(reservationRepository.selectById(1L)).willReturn(reservation);

        assertThatThrownBy(() -> reservationDeliveryService.insertMyDelivery("user01", 1L, deliveryRequest()))
                .isInstanceOf(AccessDeniedException.class);

        then(reservationDeliveryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 등록된 예약 배송 스냅샷은 중복 등록할 수 없다")
    void insert_duplicate_fail() {
        Reservation reservation = reservation(1L, "user01");
        given(reservationRepository.selectById(1L)).willReturn(reservation);
        given(reservationDeliveryJpaRepository.existsByReservation(reservation)).willReturn(true);

        assertThatThrownBy(() -> reservationDeliveryService.insert(1L, deliveryRequest()))
                .isInstanceOf(IllegalArgumentException.class);

        then(reservationDeliveryJpaRepository).should().existsByReservation(reservation);
    }

    @Test
    @DisplayName("배송 ID로 배송 스냅샷을 조회한다")
    void select_by_id() {
        ReservationDelivery delivery = new ReservationDelivery(reservation(1L, "user01"), deliveryRequest());
        given(reservationDeliveryJpaRepository.findById(10L)).willReturn(Optional.of(delivery));

        ReservationDeliveryResponse response = reservationDeliveryService.selectById(10L);

        assertThat(response.getReservationId()).isEqualTo(1L);
        assertThat(response.getRecipientName()).isEqualTo("receiver");
    }

    @Test
    @DisplayName("사용자는 본인 예약 배송 스냅샷만 조회한다")
    void select_my_by_reservation_id() {
        Reservation reservation = reservation(1L, "user01");
        ReservationDelivery delivery = new ReservationDelivery(reservation, deliveryRequest());
        given(reservationRepository.selectById(1L)).willReturn(reservation);
        given(reservationDeliveryJpaRepository.findByReservation(reservation)).willReturn(Optional.of(delivery));

        ReservationDeliveryResponse response = reservationDeliveryService.selectMyByReservationId("user01", 1L);

        assertThat(response.getRecipientName()).isEqualTo("receiver");
    }

    private Reservation reservation(Long reservationId, String userId) {
        return Reservation.builder()
                .reservationId(reservationId)
                .orderId("order-1")
                .userId(userId)
                .event(event())
                .status(ReservationStatus.PENDING_PAYMENT)
                .reservedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Event event() {
        return Event.builder()
                .eventId(1L)
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
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
