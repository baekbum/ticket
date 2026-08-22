package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.CardPaymentRefundService;
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
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private CardPaymentRefundService cardPaymentRefundService;

    @Mock
    private TicketJpaRepository ticketJpaRepository;

    @Mock
    private ReservationDiscountJpaRepository reservationDiscountJpaRepository;

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
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(
                ticket(1L, reservation, TicketStatus.CANCELLED),
                ticket(2L, reservation, TicketStatus.PENDING_PAYMENT)
        ));

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(info.getUserId()).isEqualTo("user01");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        then(repository).should().selectById(1L);
        then(repository).should().cancel(1L, info);
        then(seatCacheService).should().syncAvailableSeatsAfterCommit(cancelledSeats);
        then(seatCacheService).should().updateUserPurchaseLimit(cancelledSeats.get(0).getEvent(), "user01", 1, "SUB");
    }

    @Test
    @DisplayName("카드 결제 완료 본인 예매 부분 취소는 선택 티켓 비율만큼 부분 환불한다")
    void refund_card_payment_before_partial_my_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        List<Seat> cancelledSeats = List.of(seat(1L, event(), "VIP", 1, 1));
        Ticket selectedTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket remainingTicket = ticket(2L, reservation, TicketStatus.PAID);
        Ticket cancelledTicket = ticket(1L, reservation, TicketStatus.CANCELLED);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation))
                .willReturn(List.of(selectedTicket, remainingTicket))
                .willReturn(List.of(cancelledTicket, remainingTicket));
        given(repository.cancel(1L, info)).willReturn(cancelledSeats);

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(info.getUserId()).isEqualTo("user01");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        then(cardPaymentRefundService).should().refundPartial(payment, 125000);
        then(repository).should().cancel(1L, info);
    }

    @Test
    @DisplayName("카드 결제 완료 본인 예매 전체 취소는 gateway 환불 후 예매를 취소한다")
    void refund_card_payment_before_full_my_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L, 2L))
                .build();
        List<Seat> cancelledSeats = List.of(seat(1L, event(), "VIP", 1, 1));
        Ticket firstTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket secondTicket = ticket(2L, reservation, TicketStatus.PAID);
        Ticket firstCancelledTicket = ticket(1L, reservation, TicketStatus.CANCELLED);
        Ticket secondCancelledTicket = ticket(2L, reservation, TicketStatus.CANCELLED);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation))
                .willReturn(List.of(firstTicket, secondTicket))
                .willReturn(List.of(firstCancelledTicket, secondCancelledTicket));
        given(repository.cancel(1L, info)).willReturn(cancelledSeats);

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(info.getUserId()).isEqualTo("user01");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        then(cardPaymentRefundService).should().refundAll(payment);
        then(repository).should().cancel(1L, info);
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
        return reservation(reservationId, orderId, userId, event, ReservationStatus.PENDING_PAYMENT);
    }

    private Reservation reservation(Long reservationId, String orderId, String userId, Event event, ReservationStatus status) {
        return Reservation.builder()
                .reservationId(reservationId)
                .orderId(orderId)
                .userId(userId)
                .event(event)
                .status(status)
                .reservedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Payment cardPayment(Reservation reservation) {
        return Payment.builder()
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PAID)
                .amount(250000)
                .cardInfo(CardPaymentInfo.builder()
                        .transactionId("CARD-1")
                        .cardCompany(CardCompany.SHINHAN)
                        .maskedCardNumber("4111-****-****-1111")
                        .build())
                .requestedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Ticket ticket(Long ticketId, Reservation reservation, TicketStatus status) {
        return Ticket.builder()
                .ticketId(ticketId)
                .userId(reservation.getUserId())
                .reservation(reservation)
                .event(reservation.getEvent())
                .seat(seat(ticketId, reservation.getEvent(), "VIP", 1, ticketId.intValue()))
                .status(status)
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
