package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.service.reservation.reservation.ReservationService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("ID로 예약을 조회한다")
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
    @DisplayName("본인 예약을 ID로 조회한다")
    void reservation_select_my_reservation() {
        Reservation reservation = reservation(1L, "order-1", "user01", event());
        given(repository.selectById(1L)).willReturn(reservation);

        ReservationResponse response = reservationService.selectMyReservation("user01", 1L);

        assertThat(response.getReservationId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo("user01");
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("다른 사용자의 예약은 조회할 수 없다")
    void reservation_select_my_reservation_forbidden() {
        Reservation reservation = reservation(1L, "order-1", "other-user", event());
        given(repository.selectById(1L)).willReturn(reservation);

        assertThatThrownBy(() -> reservationService.selectMyReservation("user01", 1L))
                .isInstanceOf(AccessDeniedException.class);

        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("조건으로 예약 목록을 조회한다")
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
    @DisplayName("본인 예약 목록 조회는 로그인 사용자 ID로 검색한다")
    void reservation_select_my_reservations() {
        ReservationCondRequest cond = ReservationCondRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .page(0)
                .size(10)
                .build();
        Reservation reservation = reservation(1L, "order-1", "user01", event());

        given(repository.selectByCond(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(reservation)));

        CustomPageResponse<ReservationResponse> response = reservationService.selectMyReservations("user01", cond);

        assertThat(cond.getUserId()).isEqualTo("user01");
        assertThat(response.getContent()).hasSize(1);
        then(repository).should().selectByCond(eq(cond), any(Pageable.class));
    }

    @Test
    @DisplayName("예약 취소 후 좌석 캐시와 구매 제한 캐시를 갱신한다")
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
    @DisplayName("본인 예약 취소는 로그인 사용자 ID로 취소한다")
    void reservation_cancel_my_reservation() {
        Reservation reservation = reservation(1L, "order-1", "user01", event());
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        List<Seat> cancelledSeats = List.of(seat(1L, event(), "VIP", 1, 1));

        given(repository.selectById(1L)).willReturn(reservation);
        given(repository.cancel(1L, info)).willReturn(cancelledSeats);

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(info.getUserId()).isEqualTo("user01");
        then(repository).should().selectById(1L);
        then(repository).should().cancel(1L, info);
        then(seatCacheService).should().syncAvailableSeatsAfterCommit(cancelledSeats);
        then(seatCacheService).should().updateUserPurchaseLimit(1L, "user01", 1, "SUB");
    }

    @Test
    @DisplayName("다른 사용자의 예약은 취소할 수 없다")
    void reservation_cancel_my_reservation_forbidden() {
        Reservation reservation = reservation(1L, "order-1", "other-user", event());
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();

        given(repository.selectById(1L)).willReturn(reservation);

        assertThatThrownBy(() -> reservationService.cancelMyReservation("user01", 1L, info))
                .isInstanceOf(AccessDeniedException.class);

        then(repository).should().selectById(1L);
        then(repository).shouldHaveNoMoreInteractions();
        then(seatCacheService).shouldHaveNoInteractions();
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
