package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.checkout.CheckoutAttempt;
import dev.bum.ticket_service.jpa.checkout.CheckoutAttemptJpaRepository;
import dev.bum.ticket_service.jpa.checkout.CheckoutAttemptStatus;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.service.checkout.CheckoutService;
import dev.bum.ticket_service.service.checkout.CheckoutIdempotencyKeyGenerator;
import dev.bum.ticket_service.service.checkout.payment.CheckoutPaymentService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
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
    private CheckoutAttemptJpaRepository checkoutAttemptJpaRepository;

    @Mock
    private CheckoutPaymentService checkoutPaymentService;

    @Mock
    private PaymentGatewayCardClient paymentGatewayCardClient;

    @Spy
    private CheckoutIdempotencyKeyGenerator idempotencyKeyGenerator;

    @InjectMocks
    private CheckoutService checkoutService;

    @Test
    @DisplayName("checkout 준비 요청은 active token과 좌석 선점 상태를 검증한다")
    void prepare_validates_active_token_and_occupied_seats() {
        CheckoutPrepareRequest request = checkoutRequest();
        LocalDateTime seatExpiresAt = LocalDateTime.now().plusMinutes(9);
        given(seatCacheService.validateOccupiedSeat(1L, "user01", "order-1", request.getSeats()))
                .willReturn(seatExpiresAt);

        CheckoutPrepareResponse response = checkoutService.prepare("user01", "queue-token", request);

        assertThat(response.isPrepared()).isTrue();
        assertThat(response.getEventId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getSeats()).hasSize(1);
        assertThat(response.getIdempotencyKey()).matches("CHK-\\d{8}-[0-9a-f]{32}");
        assertThat(response.getPreparedAt()).isNotNull();
        assertThat(response.getExpiresAt()).isEqualTo(seatExpiresAt);

        ArgumentCaptor<CheckoutAttempt> checkoutAttemptCaptor = ArgumentCaptor.forClass(CheckoutAttempt.class);
        then(checkoutAttemptJpaRepository).should().save(checkoutAttemptCaptor.capture());
        CheckoutAttempt checkoutAttempt = checkoutAttemptCaptor.getValue();
        assertThat(checkoutAttempt.getIdempotencyKey()).isEqualTo(response.getIdempotencyKey());
        assertThat(checkoutAttempt.getUserId()).isEqualTo("user01");
        assertThat(checkoutAttempt.getOrderId()).isEqualTo("order-1");
        assertThat(checkoutAttempt.getEventId()).isEqualTo(1L);
        assertThat(checkoutAttempt.getPaymentNo()).startsWith("PAY-");
        assertThat(checkoutAttempt.getStatus()).isEqualTo(CheckoutAttemptStatus.PREPARED);
        assertThat(checkoutAttempt.getExpiresAt()).isEqualTo(seatExpiresAt);

        then(queueAccessService).should().validate(1L, "user01", "queue-token");
        then(seatCacheService).should().validateOccupiedSeat(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("user01"),
                org.mockito.ArgumentMatchers.eq("order-1"),
                org.mockito.ArgumentMatchers.argThat(seats -> seats.size() == 1)
        );
        then(queueAccessService).should(never()).complete(1L, "user01", "queue-token");
    }

    @Test
    @DisplayName("checkout 준비 커밋 후에도 active token을 유지하여 결제를 계속할 수 있다")
    void prepare_keeps_active_token_after_commit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            CheckoutPrepareRequest request = checkoutRequest();
            given(seatCacheService.validateOccupiedSeat(1L, "user01", "order-1", request.getSeats()))
                    .willReturn(LocalDateTime.now().plusMinutes(9));

            checkoutService.prepare("user01", "queue-token", request);

            then(queueAccessService).should().validate(1L, "user01", "queue-token");
            then(queueAccessService).should(never()).complete(1L, "user01", "queue-token");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            then(queueAccessService).should(never()).complete(1L, "user01", "queue-token");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("checkout 준비 검증에 실패하면 준비 정보를 저장하지 않는다")
    void prepare_does_not_save_attempt_when_validation_fails() {
        CheckoutPrepareRequest request = checkoutRequest();
        org.mockito.Mockito.doThrow(new dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException())
                .when(queueAccessService).validate(1L, "user01", "expired");

        assertThatThrownBy(() -> checkoutService.prepare("user01", "expired", request))
                .isInstanceOf(dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException.class);

        then(seatCacheService).shouldHaveNoInteractions();
        then(checkoutAttemptJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("checkout 확정 요청은 예약, 배송, 카드 결제 READY 정보를 생성한다")
    void confirm_creates_reservation_delivery_and_card_payment() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Seat seat = seat(event);
        new Ticket(1L, "user01", reservation, event, seat, TicketStatus.PENDING_PAYMENT);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.CREDIT_CARD);
        CheckoutAttempt checkoutAttempt = preparedAttempt();
        LocalDateTime seatExpiresAt = LocalDateTime.now().plusMinutes(8);

        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(checkoutAttempt));
        given(seatCacheService.validateOccupiedSeat(1L, "user01", "order-1", request.getSeats()))
                .willReturn(seatExpiresAt);
        given(reservationRepository.insert(org.mockito.ArgumentMatchers.any())).willReturn(reservation);
        given(reservationDiscountJpaRepository.findByReservation(reservation)).willReturn(List.of());
        given(paymentJpaRepository.save(org.mockito.ArgumentMatchers.any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = checkoutService.confirm("user01", "queue-token", request);
        then(queueAccessService).should().validate(1L, "user01", "queue-token");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(response.getAmount()).isEqualTo(187200);
        assertThat(response.getPaymentNo()).isEqualTo("PAY-PREPARED");
        assertThat(response.getAccountNumber()).isNull();
        assertThat(checkoutAttempt.getStatus()).isEqualTo(CheckoutAttemptStatus.CONFIRMED);
        assertThat(checkoutAttempt.getPayment()).isNotNull();
        assertThat(checkoutAttempt.getPayment().getExpiresAt()).isEqualTo(seatExpiresAt);

        then(seatCacheService).should().validateOccupiedSeat(1L, "user01", "order-1", request.getSeats());
        then(reservationRepository).should().insert(org.mockito.ArgumentMatchers.argThat(info ->
                info.getOrderId().equals("order-1")
                        && info.getUserId().equals("user01")
                        && info.getEventId().equals(1L)
                        && info.getSeats().size() == 1
                        && info.getUserCouponId() == null
        ));
        then(reservationDeliveryJpaRepository).should().save(org.mockito.ArgumentMatchers.any());
        then(checkoutPaymentService).should().process(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.any(Payment.class)
        );
        then(paymentJpaRepository).should().save(org.mockito.ArgumentMatchers.argThat(payment ->
                payment.getReservationFeeAmount() == 4000
                        && payment.getDeliveryFeeAmount() == 3200));
        then(seatCacheService).should(never()).updateUserPurchaseLimit(event, "user01", 1, "PLUS");
    }

    @Test
    @DisplayName("checkout 확정 요청이 무통장이면 가상계좌를 발급하고 입금 대기 결제를 생성한다")
    void confirm_issues_virtual_account_for_bank_transfer() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Seat seat = seat(event);
        new Ticket(1L, "user01", reservation, event, seat, TicketStatus.PENDING_PAYMENT);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.BANK_TRANSFER);
        request.setDelivery(null);
        CheckoutAttempt checkoutAttempt = preparedAttempt();

        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(checkoutAttempt));
        given(reservationRepository.insert(org.mockito.ArgumentMatchers.any())).willReturn(reservation);
        given(reservationDiscountJpaRepository.findByReservation(reservation)).willReturn(List.of());
        doAnswer(invocation -> {
            Payment payment = invocation.getArgument(1);
            payment.waitDeposit(
                    "KB국민은행",
                    "1111-2222-3333-4444",
                    LocalDateTime.of(2026, 9, 18, 23, 59, 59)
            );
            return null;
        }).when(checkoutPaymentService).process(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any(Payment.class));
        given(paymentJpaRepository.save(org.mockito.ArgumentMatchers.any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = checkoutService.confirm("user01", "queue-token", request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(response.getAmount()).isEqualTo(184000);
        then(reservationDeliveryJpaRepository).shouldHaveNoInteractions();
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(response.getBankName()).isEqualTo("KB국민은행");
        assertThat(response.getAccountNumber()).isEqualTo("1111-2222-3333-4444");
        then(seatCacheService).should(never()).updateUserPurchaseLimit(event, "user01", 1, "PLUS");
        then(checkoutPaymentService).should().process(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any(Payment.class));
    }

    @Test
    @DisplayName("같은 멱등 키로 생성된 checkout 확정 결과가 있으면 기존 결제를 반환한다")
    void confirm_returns_existing_payment_for_same_idempotency_key() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.CREDIT_CARD);
        CheckoutAttempt checkoutAttempt = preparedAttempt();
        checkoutAttempt.confirm(payment);

        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(checkoutAttempt));

        PaymentResponse response = checkoutService.confirm("user01", "queue-token", request);
        then(queueAccessService).shouldHaveNoInteractions();

        assertThat(response.getPaymentNo()).isEqualTo("PAY-1");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.READY);
        then(seatCacheService).shouldHaveNoInteractions();
        then(reservationRepository).shouldHaveNoInteractions();
        then(paymentGatewayCardClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("카드 READY 결제에 승인 이력이 없으면 같은 결제번호로 무통장 전환한다")
    void confirm_switches_card_to_bank_transfer_before_approval() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.READY)
                .amount(180000)
                .idempotencyKey("idem-1")
                .requestedAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(LocalDateTime.now().plusMinutes(8))
                .build();
        CheckoutAttempt checkoutAttempt = confirmedAttempt(payment);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.BANK_TRANSFER);
        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(checkoutAttempt));
        given(paymentJpaRepository.findByPaymentNoForUpdate("PAY-1")).willReturn(Optional.of(payment));
        given(paymentGatewayCardClient.hasApprovalHistory("PAY-1")).willReturn(false);
        doAnswer(invocation -> {
            Payment switchingPayment = invocation.getArgument(1);
            switchingPayment.waitDeposit("KB국민은행", "1111-2222-3333-4444", LocalDateTime.now().plusDays(1));
            return null;
        }).when(checkoutPaymentService).process(request, payment);

        PaymentResponse response = checkoutService.confirm("user01", "queue-token", request);

        assertThat(response.getPaymentNo()).isEqualTo("PAY-1");
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(response.getAccountNumber()).isEqualTo("1111-2222-3333-4444");
        then(paymentGatewayCardClient).should().hasApprovalHistory("PAY-1");
        then(queueAccessService).should().validate(1L, "user01", "queue-token");
        then(reservationRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PG에 카드 승인 이력이 있으면 READY 결제도 무통장으로 바꾸지 않는다")
    void confirm_rejects_card_to_bank_transfer_after_approval() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = Payment.builder()
                .paymentId(1L).reservation(reservation).paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD).status(PaymentStatus.READY)
                .amount(180000).idempotencyKey("idem-1")
                .expiresAt(LocalDateTime.now().plusMinutes(8)).build();
        CheckoutAttempt checkoutAttempt = confirmedAttempt(payment);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.BANK_TRANSFER);
        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(checkoutAttempt));
        given(paymentJpaRepository.findByPaymentNoForUpdate("PAY-1")).willReturn(Optional.of(payment));
        given(paymentGatewayCardClient.hasApprovalHistory("PAY-1")).willReturn(true);

        assertThatThrownBy(() -> checkoutService.confirm("user01", "queue-token", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("카드 승인 이력이 있어 결제 수단을 변경할 수 없습니다.");
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        then(checkoutPaymentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("발급된 무통장 결제는 카드로 변경하지 않는다")
    void confirm_rejects_bank_transfer_to_card() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = Payment.builder()
                .paymentId(1L).reservation(reservation).paymentNo("PAY-1")
                .method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.WAITING_DEPOSIT)
                .amount(180000).idempotencyKey("idem-1").build();
        CheckoutAttempt checkoutAttempt = confirmedAttempt(payment);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.CREDIT_CARD);
        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(checkoutAttempt));

        assertThatThrownBy(() -> checkoutService.confirm("user01", "queue-token", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("변경할 수 없는 결제 수단입니다.");
        then(paymentGatewayCardClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("확정된 checkout은 결제 상태와 관계없이 같은 결제 결과를 반환한다")
    void confirm_returns_same_terminal_payment_for_confirmed_checkout() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation);
        payment.cancel();
        CheckoutAttempt checkoutAttempt = preparedAttempt();
        checkoutAttempt.confirm(payment);
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.CREDIT_CARD);

        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(checkoutAttempt));

        PaymentResponse response = checkoutService.confirm("user01", "queue-token", request);

        assertThat(response.getPaymentNo()).isEqualTo("PAY-1");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        then(queueAccessService).shouldHaveNoInteractions();
        then(reservationRepository).shouldHaveNoInteractions();
        then(paymentJpaRepository).shouldHaveNoInteractions();
    }

    private CheckoutPrepareRequest checkoutRequest() {
        return CheckoutPrepareRequest.builder()
                .orderId("order-1")
                .eventId(1L)
                .seats(List.of(SeatInfo.builder()
                        .id(1L)
                        .zone("VIP")
                        .row(1)
                        .col(1)
                        .build()))
                .build();
    }

    @Test
    void confirm_rejects_expired_token_before_creating_reservation() {
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.BANK_TRANSFER);
        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(preparedAttempt()));
        org.mockito.Mockito.doThrow(new dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException())
                .when(queueAccessService).validate(1L, "user01", "expired");

        assertThatThrownBy(() -> checkoutService.confirm("user01", "expired", request))
                .isInstanceOf(dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException.class);
        then(reservationRepository).shouldHaveNoInteractions();
        then(seatCacheService).shouldHaveNoInteractions();
        then(checkoutPaymentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("좌석 선점 시간이 지난 checkout 확정 요청은 결제를 생성하지 않는다")
    void confirm_rejects_expired_checkout_attempt() {
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.CREDIT_CARD);
        CheckoutAttempt checkoutAttempt = CheckoutAttempt.prepare(
                "idem-1",
                "user01",
                "order-1",
                1L,
                "PAY-PREPARED",
                LocalDateTime.now().minusSeconds(1)
        );
        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(checkoutAttempt));

        assertThatThrownBy(() -> checkoutService.confirm("user01", "queue-token", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("좌석 선점 시간이 만료되었습니다.");

        then(queueAccessService).shouldHaveNoInteractions();
        then(reservationRepository).shouldHaveNoInteractions();
        then(paymentJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 사용자가 발급받은 멱등 키로 checkout을 확정할 수 없다")
    void confirm_rejects_another_users_checkout_attempt() {
        CheckoutConfirmRequest request = confirmRequest(PaymentMethod.CREDIT_CARD);
        given(checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .willReturn(Optional.of(preparedAttempt()));

        assertThatThrownBy(() -> checkoutService.confirm("other-user", "queue-token", request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessage("다른 사용자의 결제 요청 키입니다.");

        then(queueAccessService).shouldHaveNoInteractions();
        then(reservationRepository).shouldHaveNoInteractions();
        then(paymentJpaRepository).shouldHaveNoInteractions();
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

    private CheckoutAttempt preparedAttempt() {
        return CheckoutAttempt.prepare(
                "idem-1",
                "user01",
                "order-1",
                1L,
                "PAY-PREPARED",
                LocalDateTime.now().plusMinutes(9)
        );
    }

    private CheckoutAttempt confirmedAttempt(Payment payment) {
        CheckoutAttempt checkoutAttempt = CheckoutAttempt.prepare(
                "idem-1", "user01", "order-1", 1L, payment.getPaymentNo(),
                LocalDateTime.now().plusMinutes(9)
        );
        checkoutAttempt.confirm(payment);
        return checkoutAttempt;
    }

}
