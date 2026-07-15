package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.IsReservableRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.kafka.reservation.ReservationProducer;
import dev.bum.ticket_service.service.reservation.ReservationService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @InjectMocks
    private ReservationService reservationService;

    @Mock
    private ReservationRepository repository;

    @Mock
    private SeatCacheService seatCacheService;

    @Mock
    private ReservationProducer reservationProducer;

    @Test
    @DisplayName("예약 등록 요청 시 좌석 점유 검증 후 큐로 전송")
    void reservation_insert() {
        InsertReservationRequest info = insertRequest();

        reservationService.insert(info);

        then(seatCacheService).should().validateOccupiedSeat(info);
        then(reservationProducer).should().send(info);
    }

    @Test
    @DisplayName("큐 예약 생성 후 예약 좌석 캐시와 구매 제한 캐시 동기화")
    void create_reservation_from_queue() {
        InsertReservationRequest info = insertRequest();
        Reservation reservation = reservation(1L, "order-1", "user01", event());
        Seat firstSeat = seat(1L, reservation.getEvent(), "VIP", 1, 1);
        Seat secondSeat = seat(2L, reservation.getEvent(), "VIP", 1, 2);
        new Ticket(1L, "user01", reservation, reservation.getEvent(), firstSeat, TicketStatus.PENDING_PAYMENT);
        new Ticket(2L, "user01", reservation, reservation.getEvent(), secondSeat, TicketStatus.PENDING_PAYMENT);

        given(repository.insert(info)).willReturn(reservation);

        reservationService.createReservationFromQueue(info);

        then(repository).should().insert(info);
        then(seatCacheService).should().syncReservedSeatsAfterCommit(List.of(firstSeat, secondSeat));
        then(seatCacheService).should().updateUserPurchaseLimit(1L, "user01", 2, "PLUS");
    }

    @Test
    @DisplayName("ID로 예약 조회")
    void reservation_select_by_id() {
        Reservation reservation = reservation(1L, "order-1", "user01", event());
        new Ticket(1L, "user01", reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);

        given(repository.selectById(1L)).willReturn(reservation);

        ReservationResponse response = reservationService.selectById(1L);

        assertThat(response.getReservationId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getUserId()).isEqualTo("user01");
        assertThat(response.getTicketCount()).isEqualTo(1);
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("조건으로 예약 조회")
    void reservation_select_by_cond() {
        ReservationCondRequest cond = ReservationCondRequest.builder()
                .userId("user01")
                .eventId(1L)
                .page(0)
                .size(10)
                .sort(List.of("reservationId-desc"))
                .build();
        Reservation reservation = reservation(1L, "order-1", "user01", event());

        given(repository.selectByCond(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(reservation)));

        CustomPageResponse<ReservationResponse> response = reservationService.selectByCond(cond);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getReservationId()).isEqualTo(1L);
        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
        then(repository).should().selectByCond(
                eq(cond),
                argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 10
                        && pageable.getSort().getOrderFor("reservationId") != null)
        );
    }

    @Test
    @DisplayName("예약 취소 후 좌석 캐시와 구매 제한 캐시 동기화")
    void reservation_cancel() {
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        List<Seat> cancelledSeats = List.of(seat(1L, event(), "VIP", 1, 1));

        given(repository.cancel(1L, info)).willReturn(cancelledSeats);

        reservationService.cancel(1L, info);

        then(repository).should().cancel(1L, info);
        then(seatCacheService).should().syncAvailableSeatsAfterCommit(cancelledSeats);
        then(seatCacheService).should().updateUserPurchaseLimit(1L, "user01", 1, "SUB");
    }

    @Test
    @DisplayName("예약 가능 여부 확인")
    void reservation_is_reservable() {
        IsReservableRequest info = IsReservableRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedSeatCnt(2)
                .build();

        reservationService.isReservable(info);

        then(repository).should().validateReservableFromDatabase("user01", 1L, 2);
    }

    private InsertReservationRequest insertRequest() {
        return InsertReservationRequest.builder()
                .orderId("order-1")
                .userId("user01")
                .eventId(1L)
                .seats(List.of(
                        SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build(),
                        SeatInfo.builder().id(2L).zone("VIP").row(1).col(2).build()
                ))
                .build();
    }

    private Reservation reservation(Long reservationId, String orderId, String userId, Event event) {
        return Reservation.builder()
                .reservationId(reservationId)
                .orderId(orderId)
                .userId(userId)
                .event(event)
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

    private Seat seat(Long seatId, Event event, String zone, Integer row, Integer col) {
        return Seat.builder()
                .seatId(seatId)
                .event(event)
                .zone(zone)
                .seatRow(row)
                .seatCol(col)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(SeatStatus.AVAILABLE)
                .build();
    }
}
