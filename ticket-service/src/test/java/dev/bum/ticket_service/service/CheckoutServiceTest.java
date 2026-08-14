package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.service.checkout.CheckoutService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CheckoutServiceTest {

    @Mock
    private SeatCacheService seatCacheService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationDiscountJpaRepository reservationDiscountJpaRepository;

    @Mock
    private ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @InjectMocks
    private CheckoutService checkoutService;

    @Test
    @DisplayName("같은 멱등 키로 생성된 checkout 준비 결과가 있으면 기존 응답을 반환한다")
    void prepare_returns_existing_response_for_same_idempotency_key() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Seat seat = seat(event);
        new Ticket(1L, "user01", reservation, event, seat, TicketStatus.PENDING_PAYMENT);
        Payment payment = payment(reservation, "idem-1");
        CheckoutPrepareRequest request = checkoutRequest("idem-1");

        given(paymentJpaRepository.findByIdempotencyKey("idem-1")).willReturn(Optional.of(payment));
        given(reservationDiscountJpaRepository.findByReservation(reservation)).willReturn(List.of());

        CheckoutPrepareResponse response = checkoutService.prepare("user01", request);

        assertThat(response.getReservationId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getPaymentId()).isEqualTo(1L);
        assertThat(response.getPaymentNo()).isEqualTo("PAY-1");
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(response.getAmount()).isEqualTo(180000);
        assertThat(response.getPaymentExpiresAt()).isEqualTo(payment.getExpiresAt());
        assertThat(response.getPaymentExpiresInSeconds()).isPositive();

        then(seatCacheService).shouldHaveNoInteractions();
        then(reservationRepository).shouldHaveNoInteractions();
        then(reservationDeliveryJpaRepository).shouldHaveNoInteractions();
        then(paymentJpaRepository).should(never()).save(org.mockito.ArgumentMatchers.any(Payment.class));
    }

    @Test
    @DisplayName("다른 사용자의 멱등 키 재사용은 차단한다")
    void prepare_rejects_other_user_idempotency_key() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, "idem-1");
        CheckoutPrepareRequest request = checkoutRequest("idem-1");

        given(paymentJpaRepository.findByIdempotencyKey("idem-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> checkoutService.prepare("other-user", request))
                .isInstanceOf(AccessDeniedException.class);

        then(seatCacheService).shouldHaveNoInteractions();
        then(reservationRepository).shouldHaveNoInteractions();
        then(reservationDeliveryJpaRepository).shouldHaveNoInteractions();
        then(paymentJpaRepository).should(never()).save(org.mockito.ArgumentMatchers.any(Payment.class));
    }

    @Test
    @DisplayName("checkout 최초 준비 요청은 좌석 선점 검증 후 예매, 배송, 결제, 구매 제한을 생성한다")
    void prepare_creates_reservation_delivery_payment_and_purchase_limit() {
        Event event = event();
        Reservation reservation = reservation(event, "user01");
        Seat seat = seat(event);
        new Ticket(1L, "user01", reservation, event, seat, TicketStatus.PENDING_PAYMENT);
        CheckoutPrepareRequest request = checkoutRequest("  idem-1  ");

        given(paymentJpaRepository.findByIdempotencyKey("idem-1")).willReturn(Optional.empty());
        given(reservationRepository.insert(org.mockito.ArgumentMatchers.any())).willReturn(reservation);
        given(reservationDiscountJpaRepository.findByReservation(reservation)).willReturn(List.of());
        given(paymentJpaRepository.save(org.mockito.ArgumentMatchers.any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CheckoutPrepareResponse response = checkoutService.prepare("user01", request);

        assertThat(response.getReservationId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(response.getAmount()).isEqualTo(180000);
        assertThat(response.getPaymentExpiresAt()).isNotNull();
        assertThat(response.getPaymentExpiresInSeconds()).isBetween(0L, 600L);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        then(seatCacheService).should().validateOccupiedSeat(org.mockito.ArgumentMatchers.argThat(info ->
                info.getOrderId().equals("order-1")
                        && info.getUserId().equals("user01")
                        && info.getEventId().equals(1L)
                        && info.getSeats().size() == 1
        ));
        then(reservationRepository).should().insert(org.mockito.ArgumentMatchers.any());
        then(reservationDeliveryJpaRepository).should().save(org.mockito.ArgumentMatchers.any());
        then(paymentJpaRepository).should().save(paymentCaptor.capture());
        then(seatCacheService).should().updateUserPurchaseLimit(reservation.getEvent(), "user01", 1, "PLUS");

        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getReservation()).isEqualTo(reservation);
        assertThat(savedPayment.getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(savedPayment.getAmount()).isEqualTo(180000);
        assertThat(savedPayment.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(savedPayment.getPaymentNo()).startsWith("PAY-");
        assertThat(savedPayment.getExpiresAt()).isAfter(savedPayment.getRequestedAt());
    }

    @Test
    @DisplayName("checkout 준비 요청에 멱등 키가 없으면 거부한다")
    void prepare_rejects_missing_idempotency_key() {
        CheckoutPrepareRequest request = checkoutRequest(" ");

        assertThatThrownBy(() -> checkoutService.prepare("user01", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 멱등 키가 필요합니다.");

        then(paymentJpaRepository).shouldHaveNoInteractions();
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
                .delivery(ReservationDeliveryRequest.builder()
                        .recipientName("홍길동")
                        .recipientPhone("010-0000-0000")
                        .zipCode("12345")
                        .address("서울시 강남구")
                        .detailAddress("101호")
                        .build())
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .idempotencyKey(idempotencyKey)
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

    private Payment payment(Reservation reservation, String idempotencyKey) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.READY)
                .amount(180000)
                .idempotencyKey(idempotencyKey)
                .requestedAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(LocalDateTime.now().plusMinutes(9))
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
}
