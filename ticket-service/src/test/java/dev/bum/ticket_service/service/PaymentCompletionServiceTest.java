package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.payment.PaymentCompletionService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PaymentCompletionServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private SeatCacheService seatCacheService;

    @InjectMocks
    private PaymentCompletionService paymentCompletionService;

    @Test
    @DisplayName("카드 결제 완료 시 결제와 예매를 확정하고 구매 카운트를 증가시킨다")
    void complete_card_payment() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        Seat seat = seat(event);
        Ticket ticket = ticket(event, reservation, seat);

        given(ticketRepository.selectByReservation(reservation)).willReturn(List.of(ticket));

        PaymentResponse response = paymentCompletionService.complete(payment, LocalDateTime.of(2026, 8, 19, 12, 0));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        then(seatCacheService).should().updateUserPurchaseLimit(event, "user01", 1, "PLUS");
        then(seatCacheService).should().syncReservedSeatsAfterCommit(List.of(seat));
    }

    @Test
    @DisplayName("무통장 입금 완료 시 입금자명을 저장하고 구매 카운트를 증가시킨다")
    void complete_virtual_account_deposit() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Payment payment = virtualAccountPayment(reservation);
        Seat seat = seat(event);
        Ticket ticket = ticket(event, reservation, seat);

        given(ticketRepository.selectByReservation(reservation)).willReturn(List.of(ticket));

        PaymentResponse response = paymentCompletionService.completeDeposit(
                payment,
                LocalDateTime.of(2026, 8, 19, 12, 0),
                "아이유"
        );

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getDepositorName()).isEqualTo("아이유");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        then(seatCacheService).should().updateUserPurchaseLimit(event, "user01", 1, "PLUS");
        then(seatCacheService).should().syncReservedSeatsAfterCommit(List.of(seat));
    }

    @Test
    @DisplayName("이미 완료된 결제 완료 요청은 구매 카운트를 다시 증가시키지 않는다")
    void skip_side_effects_when_payment_already_paid() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.PAID);

        PaymentResponse response = paymentCompletionService.complete(payment, LocalDateTime.of(2026, 8, 19, 12, 0));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        then(ticketRepository).shouldHaveNoInteractions();
        then(seatCacheService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("종료된 결제는 완료 처리할 수 없다")
    void reject_completion_when_payment_is_terminal() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.EXPIRED);

        assertThatThrownBy(() -> paymentCompletionService.complete(payment, LocalDateTime.of(2026, 8, 19, 12, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 완료 처리할 수 없는 상태입니다.");

        then(ticketRepository).shouldHaveNoInteractions();
        then(seatCacheService).shouldHaveNoInteractions();
    }

    private Event event() {
        return Event.builder()
                .eventId(1L)
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

    private Reservation reservation(Event event, String userId) {
        return Reservation.builder()
                .reservationId(1L)
                .orderId("ORDER-1")
                .userId(userId)
                .event(event)
                .status(ReservationStatus.PENDING_PAYMENT)
                .tickets(new ArrayList<>())
                .reservedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .build();
    }

    private Payment payment(Reservation reservation, PaymentMethod method, PaymentStatus status) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(method)
                .status(status)
                .amount(180000)
                .requestedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .build();
    }

    private Payment virtualAccountPayment(Reservation reservation) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.WAITING_DEPOSIT)
                .amount(180000)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
                .requestedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .expiresAt(LocalDateTime.of(2099, 7, 27, 23, 59, 59))
                .build();
    }

    private Seat seat(Event event) {
        return Seat.builder()
                .seatId(1L)
                .event(event)
                .zone("VIP")
                .seatRow(1)
                .seatCol(1)
                .grade(SeatGrade.VIP)
                .price(180000)
                .status(SeatStatus.LOCKED)
                .build();
    }

    private Ticket ticket(Event event, Reservation reservation, Seat seat) {
        return Ticket.builder()
                .ticketId(1L)
                .userId("user01")
                .reservation(reservation)
                .event(event)
                .seat(seat)
                .status(TicketStatus.PENDING_PAYMENT)
                .build();
    }
}
