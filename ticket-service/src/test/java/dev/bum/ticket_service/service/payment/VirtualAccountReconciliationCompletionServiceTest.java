package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.payment.VirtualAccountPaymentInfo;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class VirtualAccountReconciliationCompletionServiceTest {

    private static final String PAYMENT_NO = "PAY-20260929120000-123456789abc";
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 9, 28, 23, 59, 59);
    private static final LocalDateTime DEPOSITED_AT = EXPIRES_AT.minusMinutes(1);

    @Mock private PaymentJpaRepository payments;
    @Mock private TicketRepository tickets;
    @Mock private SeatCacheService seatCache;

    private VirtualAccountReconciliationCompletionService service;

    @BeforeEach
    void setUp() {
        service = new VirtualAccountReconciliationCompletionService(
                payments,
                new VirtualAccountReconciliationValidator(tickets),
                new PaymentCompletionService(tickets, seatCache)
        );
    }

    @Test
    void deposited_before_deadline_completes_all_ticket_entities_even_when_admin_checks_later() {
        Fixture fixture = fixture(PaymentStatus.WAITING_DEPOSIT, SeatStatus.LOCKED);
        given(payments.findByPaymentNoForUpdate(PAYMENT_NO)).willReturn(Optional.of(fixture.payment));
        given(tickets.selectByReservation(fixture.reservation)).willReturn(List.of(fixture.ticket));

        var response = service.complete(PAYMENT_NO, gatewayStatus(GatewayVirtualAccountStatus.TICKET_PAYMENT_FAILED, 180000));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(fixture.reservation.getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(fixture.ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(fixture.seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    void waiting_gateway_payment_cannot_be_manually_completed() {
        Fixture fixture = fixture(PaymentStatus.WAITING_DEPOSIT, SeatStatus.LOCKED);
        given(payments.findByPaymentNoForUpdate(PAYMENT_NO)).willReturn(Optional.of(fixture.payment));

        assertThatThrownBy(() -> service.complete(PAYMENT_NO, gatewayStatus(GatewayVirtualAccountStatus.WAITING_DEPOSIT, 180000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PG에서 입금 완료가 확인되지 않았습니다");
        then(seatCache).shouldHaveNoInteractions();
    }

    @Test
    void expired_ticket_payment_cannot_be_revived() {
        Fixture fixture = fixture(PaymentStatus.EXPIRED, SeatStatus.AVAILABLE);
        given(payments.findByPaymentNoForUpdate(PAYMENT_NO)).willReturn(Optional.of(fixture.payment));

        assertThatThrownBy(() -> service.complete(PAYMENT_NO, gatewayStatus(GatewayVirtualAccountStatus.DEPOSITED, 180000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("입금 대기 상태가 아닙니다");
        then(seatCache).shouldHaveNoInteractions();
    }

    @Test
    void mismatched_amount_or_released_seat_cannot_be_completed() {
        Fixture fixture = fixture(PaymentStatus.WAITING_DEPOSIT, SeatStatus.AVAILABLE);
        given(payments.findByPaymentNoForUpdate(PAYMENT_NO)).willReturn(Optional.of(fixture.payment));

        assertThatThrownBy(() -> service.complete(PAYMENT_NO, gatewayStatus(GatewayVirtualAccountStatus.DEPOSITED, 170000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("금액");

        given(tickets.selectByReservation(fixture.reservation)).willReturn(List.of(fixture.ticket));
        assertThatThrownBy(() -> service.complete(PAYMENT_NO, gatewayStatus(GatewayVirtualAccountStatus.DEPOSITED, 180000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("좌석 상태");
    }

    private GatewayVirtualAccountStatusResponse gatewayStatus(GatewayVirtualAccountStatus status, int amount) {
        return new GatewayVirtualAccountStatusResponse(
                PAYMENT_NO, BankCompany.KB, "KB국민은행", "1111-2222-3333-4444", "홍길동",
                BigDecimal.valueOf(amount), EXPIRES_AT, DEPOSITED_AT, status
        );
    }

    private Fixture fixture(PaymentStatus status, SeatStatus seatStatus) {
        Event event = Event.builder().eventId(1L).build();
        Reservation reservation = Reservation.builder()
                .reservationId(1L).orderId("ORDER-1").userId("user01").event(event)
                .status(ReservationStatus.PENDING_PAYMENT).tickets(new ArrayList<>()).build();
        Seat seat = Seat.builder().seatId(1L).event(event).status(seatStatus).build();
        Ticket ticket = Ticket.builder().ticketId(1L).userId("user01").reservation(reservation)
                .event(event).seat(seat).status(TicketStatus.PENDING_PAYMENT).build();
        Payment payment = Payment.builder().paymentId(1L).paymentNo(PAYMENT_NO).reservation(reservation)
                .method(PaymentMethod.BANK_TRANSFER).status(status).amount(180000).expiresAt(EXPIRES_AT)
                .virtualAccountInfo(VirtualAccountPaymentInfo.builder()
                        .bankName("KB국민은행").accountNumber("1111-2222-3333-4444").build())
                .build();
        return new Fixture(event, reservation, ticket, seat, payment);
    }

    private record Fixture(Event event, Reservation reservation, Ticket ticket, Seat seat, Payment payment) {
    }
}
