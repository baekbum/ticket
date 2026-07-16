package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.ticket.TicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @InjectMocks
    private TicketService ticketService;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Test
    @DisplayName("예약 ID로 티켓 목록 조회")
    void ticket_select_by_reservation_id() {
        Event event = event();
        Reservation reservation = reservation(1L, event);
        Ticket firstTicket = ticket(1L, reservation, event, seat(1L, event, "VIP", 1, 1));
        Ticket secondTicket = ticket(2L, reservation, event, seat(2L, event, "VIP", 1, 2));

        given(reservationRepository.selectById(1L)).willReturn(reservation);
        given(ticketRepository.selectByReservation(reservation)).willReturn(List.of(firstTicket, secondTicket));

        List<TicketResponse> response = ticketService.selectByReservationId(1L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getTicketId()).isEqualTo(1L);
        assertThat(response.get(0).getSeatId()).isEqualTo(1L);
        assertThat(response.get(0).getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT.name());
        then(reservationRepository).should().selectById(1L);
        then(ticketRepository).should().selectByReservation(reservation);
    }

    @Test
    @DisplayName("본인 예약 ID로 티켓 목록 조회")
    void ticket_select_my_tickets_by_reservation_id() {
        Event event = event();
        Reservation reservation = reservation(1L, event);
        Ticket firstTicket = ticket(1L, reservation, event, seat(1L, event, "VIP", 1, 1));
        Ticket secondTicket = ticket(2L, reservation, event, seat(2L, event, "VIP", 1, 2));

        given(reservationRepository.selectById(1L)).willReturn(reservation);
        given(ticketRepository.selectByReservation(reservation)).willReturn(List.of(firstTicket, secondTicket));

        List<TicketResponse> response = ticketService.selectMyTicketsByReservationId("user01", 1L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getTicketId()).isEqualTo(1L);
        assertThat(response.get(0).getSeatId()).isEqualTo(1L);
        assertThat(response.get(0).getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT.name());
        then(reservationRepository).should().selectById(1L);
        then(ticketRepository).should().selectByReservation(reservation);
    }

    @Test
    @DisplayName("다른 사용자의 예약 티켓은 조회할 수 없다")
    void ticket_select_my_tickets_by_reservation_id_forbidden() {
        Event event = event();
        Reservation reservation = reservation(1L, "other-user", event);

        given(reservationRepository.selectById(1L)).willReturn(reservation);

        assertThatThrownBy(() -> ticketService.selectMyTicketsByReservationId("user01", 1L))
                .isInstanceOf(AccessDeniedException.class);

        then(reservationRepository).should().selectById(1L);
        then(ticketRepository).shouldHaveNoInteractions();
    }

    private Ticket ticket(Long ticketId, Reservation reservation, Event event, Seat seat) {
        return Ticket.builder()
                .ticketId(ticketId)
                .userId("user01")
                .reservation(reservation)
                .event(event)
                .seat(seat)
                .status(TicketStatus.PENDING_PAYMENT)
                .build();
    }

    private Reservation reservation(Long reservationId, Event event) {
        return reservation(reservationId, "user01", event);
    }

    private Reservation reservation(Long reservationId, String userId, Event event) {
        return Reservation.builder()
                .reservationId(reservationId)
                .orderId("order-1")
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
                .status(SeatStatus.RESERVED)
                .build();
    }
}
