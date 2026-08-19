package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.feign.paymentgateway.GatewayVirtualAccountIssueResponse;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.service.checkout.CheckoutService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
class CheckoutServiceTest {

    @Mock
    private SeatCacheService seatCacheService;

    @Mock
    private QueueAccessService queueAccessService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;

    @Mock
    private ReservationDiscountJpaRepository reservationDiscountJpaRepository;

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private PaymentGatewayVirtualAccountClient paymentGatewayVirtualAccountClient;

    @InjectMocks
    private CheckoutService checkoutService;

    @Test
    @DisplayName("checkout 준비 요청은 active token과 좌석 선점 상태를 검증한다")
    void prepare_validates_active_token_and_occupied_seats() {
        CheckoutPrepareRequest request = checkoutRequest("  idem-1  ");

        CheckoutPrepareResponse response = checkoutService.prepare("user01", "queue-token", request);

        assertThat(response.isPrepared()).isTrue();
        assertThat(response.getEventId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getSeats()).hasSize(1);
        assertThat(response.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(response.getPreparedAt()).isNotNull();

        then(queueAccessService).should().validate(1L, "user01", "queue-token");
        then(seatCacheService).should().validateOccupiedSeat(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("user01"),
                org.mockito.ArgumentMatchers.eq("order-1"),
                org.mockito.ArgumentMatchers.argThat(seats -> seats.size() == 1)
        );
        then(queueAccessService).should().complete(1L, "user01", "queue-token");
    }

    @Test
    @DisplayName("checkout 준비 요청에 멱등 키가 없으면 거부한다")
    void prepare_rejects_missing_idempotency_key() {
        CheckoutPrepareRequest request = checkoutRequest(" ");

        assertThatThrownBy(() -> checkoutService.prepare("user01", "queue-token", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 멱등 키가 필요합니다.");

        then(queueAccessService).shouldHaveNoInteractions();
        then(seatCacheService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("checkout 준비 성공 후 active token 회수는 트랜잭션 커밋 이후 실행된다")
    void prepare_releases_active_token_after_commit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            CheckoutPrepareRequest request = checkoutRequest("idem-1");

            checkoutService.prepare("user01", "queue-token", request);

            then(queueAccessService).should().validate(1L, "user01", "queue-token");
            then(queueAccessService).should(never()).complete(1L, "user01", "queue-token");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            then(queueAccessService).should().complete(1L, "user01", "queue-token");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("checkout 확정 요청은 예약, 배송, 카드 결제 READY 정보를 생성한다")
    void confirm_creates_reservation_delivery_and_card_payment() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Seat seat = seat(event);
        new Ticket(1L, "user01", reservation, event, seat, TicketStatus.PENDING_PAYMENT);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.CREDIT_CARD);

        given(paymentJpaRepository.findByIdempotencyKey("idem-1")).willReturn(Optional.empty());
        given(reservationRepository.insert(org.mockito.ArgumentMatchers.any())).willReturn(reservation);
        given(reservationDiscountJpaRepository.findByReservation(reservation)).willReturn(List.of());
        given(paymentJpaRepository.save(org.mockito.ArgumentMatchers.any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = checkoutService.confirm("user01", request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(response.getAmount()).isEqualTo(180000);
        assertThat(response.getPaymentNo()).startsWith("PAY-");
        assertThat(response.getAccountNumber()).isNull();

        then(seatCacheService).should().validateOccupiedSeat(1L, "user01", "order-1", request.getSeats());
        then(reservationRepository).should().insert(org.mockito.ArgumentMatchers.argThat(info ->
                info.getOrderId().equals("order-1")
                        && info.getUserId().equals("user01")
                        && info.getEventId().equals(1L)
                        && info.getSeats().size() == 1
                        && info.getUserCouponId() == null
        ));
        then(reservationDeliveryJpaRepository).should().save(org.mockito.ArgumentMatchers.any());
        then(paymentJpaRepository).should().save(org.mockito.ArgumentMatchers.any(Payment.class));
        then(seatCacheService).should().updateUserPurchaseLimit(event, "user01", 1, "PLUS");
    }

    @Test
    @DisplayName("checkout 확정 요청이 무통장이면 가상계좌를 발급하고 입금 대기 결제를 생성한다")
    void confirm_issues_virtual_account_for_bank_transfer() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Seat seat = seat(event);
        new Ticket(1L, "user01", reservation, event, seat, TicketStatus.PENDING_PAYMENT);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.BANK_TRANSFER);
        GatewayVirtualAccountIssueResponse virtualAccount = virtualAccountIssueResponse();

        given(paymentJpaRepository.findByIdempotencyKey("idem-1")).willReturn(Optional.empty());
        given(reservationRepository.insert(org.mockito.ArgumentMatchers.any())).willReturn(reservation);
        given(reservationDiscountJpaRepository.findByReservation(reservation)).willReturn(List.of());
        given(paymentGatewayVirtualAccountClient.issue(org.mockito.ArgumentMatchers.any())).willReturn(virtualAccount);
        given(paymentJpaRepository.save(org.mockito.ArgumentMatchers.any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = checkoutService.confirm("user01", request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(response.getBankName()).isEqualTo("KB국민은행");
        assertThat(response.getAccountNumber()).isEqualTo("1111-2222-3333-4444");
        then(paymentGatewayVirtualAccountClient).should().issue(org.mockito.ArgumentMatchers.argThat(argument ->
                argument.getBankCompany() == BankCompany.KB
                        && argument.getAmount().compareTo(BigDecimal.valueOf(180000)) == 0
                        && argument.getEventDateTime().equals(event.getEventDateTime())
                        && Boolean.FALSE.equals(argument.getTicketPaymentApplyRequired())
        ));
    }

    @Test
    @DisplayName("같은 멱등 키로 생성된 checkout 확정 결과가 있으면 기존 결제를 반환한다")
    void confirm_returns_existing_payment_for_same_idempotency_key() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.CREDIT_CARD);

        given(paymentJpaRepository.findByIdempotencyKey("idem-1")).willReturn(Optional.of(payment));

        PaymentResponse response = checkoutService.confirm("user01", request);

        assertThat(response.getPaymentNo()).isEqualTo("PAY-1");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.READY);
        then(seatCacheService).shouldHaveNoInteractions();
        then(reservationRepository).shouldHaveNoInteractions();
    }

    private CheckoutPrepareRequest checkoutRequest(String idempotencyKey) {
        return CheckoutPrepareRequest.builder()
                .orderId("order-1")
                .eventId(1L)
                .seats(List.of(SeatInfo.builder()
                        .id(1L)
                        .zone("VIP")
                        .row(1)
                        .col(1)
                        .build()))
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private CheckoutConfirmRequest confirmRequest(PaymentMethod paymentMethod) {
        return CheckoutConfirmRequest.builder()
                .orderId("order-1")
                .eventId(1L)
                .seats(List.of(SeatInfo.builder()
                        .id(1L)
                        .zone("VIP")
                        .row(1)
                        .col(1)
                        .build()))
                .delivery(ReservationDeliveryRequest.builder()
                        .recipientName("홍길동")
                        .recipientPhone("010-0000-0000")
                        .zipCode("12345")
                        .address("서울시 강남구")
                        .detailAddress("101호")
                        .build())
                .paymentMethod(paymentMethod)
                .idempotencyKey("idem-1")
                .bankCode(paymentMethod == PaymentMethod.BANK_TRANSFER ? "KB" : null)
                .build();
    }

    private GatewayVirtualAccountIssueResponse virtualAccountIssueResponse() {
        return GatewayVirtualAccountIssueResponse.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCompany(BankCompany.KB)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
                .amount(BigDecimal.valueOf(180000))
                .expiresAt(LocalDateTime.of(2026, 9, 18, 23, 59, 59))
                .issued(true)
                .message("가상계좌가 발급되었습니다.")
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
                .orderId("order-1")
                .userId(userId)
                .event(event)
                .status(ReservationStatus.PENDING_PAYMENT)
                .tickets(new ArrayList<>())
                .reservedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
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

    private Payment payment(Reservation reservation) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.READY)
                .amount(180000)
                .idempotencyKey("idem-1")
                .requestedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .expiresAt(LocalDateTime.of(2026, 7, 27, 12, 10))
                .build();
    }
}
