package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.CardPaymentApproveRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositRequest;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssueRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.payment.MockCardAuthorizationService;
import dev.bum.ticket_service.service.payment.MockVirtualAccountIssueService;
import dev.bum.ticket_service.service.payment.PaymentService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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
class PaymentServiceTest {

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private SeatCacheService seatCacheService;

    @Mock
    private QueueAccessService queueAccessService;

    @Mock
    private MockCardAuthorizationService mockCardAuthorizationService;

    @Mock
    private MockVirtualAccountIssueService mockVirtualAccountIssueService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("카드 승인 성공 시 결제와 예매를 완료 처리한다")
    void approve_card_payment_success() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        Seat seat = seat(event);
        Ticket ticket = ticket(event, reservation, seat);
        CardPaymentApproveRequest request = cardRequest();

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));
        given(ticketRepository.selectByReservation(reservation)).willReturn(List.of(ticket));
        given(mockCardAuthorizationService.approve(request)).willReturn(true);

        PaymentResponse response = paymentService.approveCard("user01", "queue-token", request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);

        then(queueAccessService).should().validate(1L, "user01", "queue-token");
        then(seatCacheService).should().syncReservedSeatsAfterCommit(List.of(seat));
    }

    @Test
    @DisplayName("다른 사용자의 결제 승인 시도를 거부한다")
    void approve_card_payment_forbidden() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        CardPaymentApproveRequest request = cardRequest();

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.approveCard("other-user", "queue-token", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("다른 사용자의 결제 요청입니다.");

        then(queueAccessService).should(never()).validate(1L, "other-user", "queue-token");
        then(mockCardAuthorizationService).should(never()).approve(request);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("카드 정보가 일치하지 않으면 결제 상태를 유지한다")
    void approve_card_payment_invalid_card() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        CardPaymentApproveRequest request = cardRequest();

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));
        given(mockCardAuthorizationService.approve(request)).willReturn(false);

        assertThatThrownBy(() -> paymentService.approveCard("user01", "queue-token", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("카드 정보가 일치하지 않습니다.");

        then(queueAccessService).should().validate(1L, "user01", "queue-token");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("가상계좌 발급 성공 시 결제를 입금 대기 상태로 변경한다")
    void issue_virtual_account_success() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Payment payment = payment(reservation, PaymentMethod.BANK_TRANSFER, PaymentStatus.READY);
        VirtualAccountIssueRequest request = virtualAccountRequest();
        MockVirtualAccountIssueService.VirtualAccount virtualAccount =
                new MockVirtualAccountIssueService.VirtualAccount(
                        "KB국민은행",
                        "1111-2222-3333-4444",
                        LocalDateTime.of(2026, 7, 27, 23, 59, 59)
                );

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));
        given(mockVirtualAccountIssueService.issue("KB")).willReturn(virtualAccount);

        PaymentResponse response = paymentService.issueVirtualAccount("user01", "queue-token", request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(response.getBankName()).isEqualTo("KB국민은행");
        assertThat(response.getAccountNumber()).isEqualTo("1111-2222-3333-4444");
        assertThat(response.getDepositorName()).isEqualTo("홍길동");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);

        then(queueAccessService).should().validate(1L, "user01", "queue-token");
        then(paymentJpaRepository).should().existsByAccountNumber("1111-2222-3333-4444");
    }

    @Test
    @DisplayName("카드 결제 건에는 가상계좌를 발급할 수 없다")
    void issue_virtual_account_invalid_payment_method() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        VirtualAccountIssueRequest request = virtualAccountRequest();

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.issueVirtualAccount("user01", "queue-token", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("무통장 입금 결제 요청이 아닙니다.");

        then(queueAccessService).should().validate(1L, "user01", "queue-token");
        then(mockVirtualAccountIssueService).shouldHaveNoInteractions();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("가상계좌 입금 성공 시 결제와 예매를 완료 처리한다")
    void deposit_virtual_account_success() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Payment payment = virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, LocalDateTime.of(2099, 7, 27, 23, 59, 59));
        Seat seat = seat(event);
        Ticket ticket = ticket(event, reservation, seat);
        VirtualAccountDepositRequest request = virtualAccountDepositRequest(180000);

        given(paymentJpaRepository.findByAccountNumberForUpdate(request.getAccountNumber())).willReturn(Optional.of(payment));
        given(ticketRepository.selectByReservation(reservation)).willReturn(List.of(ticket));

        PaymentResponse response = paymentService.depositVirtualAccount(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);

        then(queueAccessService).shouldHaveNoInteractions();
        then(seatCacheService).should().syncReservedSeatsAfterCommit(List.of(seat));
    }

    @Test
    @DisplayName("입금 금액이 결제 금액과 다르면 결제 상태를 유지한다")
    void deposit_virtual_account_amount_mismatch() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, LocalDateTime.of(2099, 7, 27, 23, 59, 59));
        VirtualAccountDepositRequest request = virtualAccountDepositRequest(170000);

        given(paymentJpaRepository.findByAccountNumberForUpdate(request.getAccountNumber())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.depositVirtualAccount(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("입금 금액이 일치하지 않습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        then(ticketRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("입금 기한이 만료되면 결제를 만료 처리한다")
    void deposit_virtual_account_expired() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, LocalDateTime.of(2020, 1, 1, 23, 59, 59));
        VirtualAccountDepositRequest request = virtualAccountDepositRequest(180000);

        given(paymentJpaRepository.findByAccountNumberForUpdate(request.getAccountNumber())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.depositVirtualAccount(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("입금 기한이 만료되었습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        then(ticketRepository).shouldHaveNoInteractions();
    }

    private CardPaymentApproveRequest cardRequest() {
        return CardPaymentApproveRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .cardCompany("KB")
                .cardNumber("1234-5678-9012-3456")
                .cvc("123")
                .cardPassword("12")
                .build();
    }

    private VirtualAccountIssueRequest virtualAccountRequest() {
        return VirtualAccountIssueRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCode("KB")
                .depositorName("홍길동")
                .build();
    }

    private VirtualAccountDepositRequest virtualAccountDepositRequest(Integer amount) {
        return VirtualAccountDepositRequest.builder()
                .accountNumber("1111-2222-3333-4444")
                .amount(amount)
                .build();
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

    private Payment virtualAccountPayment(Reservation reservation, PaymentStatus status, LocalDateTime expiresAt) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(status)
                .amount(180000)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
                .depositorName("홍길동")
                .requestedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .expiresAt(expiresAt)
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
