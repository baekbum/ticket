package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.coupon.coupon.enums.DiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCoupon;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.payment.PaymentExpirationService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationServiceTest {

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;

    @Mock
    private ReservationDiscountJpaRepository reservationDiscountJpaRepository;

    @Mock
    private SeatCacheService seatCacheService;

    @InjectMocks
    private PaymentExpirationService paymentExpirationService;

    @Test
    @DisplayName("만료 결제 처리 시 결제, 예약, 티켓, 좌석, 쿠폰을 정리한다")
    void expire_if_expired() {
        Event event = event();
        Reservation reservation = reservation(event);
        Payment payment = payment(reservation, PaymentStatus.WAITING_DEPOSIT, LocalDateTime.of(2020, 1, 1, 0, 0));
        Seat seat = seat(event);
        Ticket ticket = ticket(event, reservation, seat);
        UserCoupon userCoupon = userCoupon(LocalDateTime.of(2099, 1, 1, 0, 0));
        ReservationDiscount discount = reservationDiscount(reservation, userCoupon);

        given(paymentJpaRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        given(ticketRepository.selectByReservation(reservation)).willReturn(List.of(ticket));
        given(reservationDeliveryJpaRepository.findByReservation(reservation)).willReturn(Optional.empty());
        given(reservationDiscountJpaRepository.findByReservation(reservation)).willReturn(List.of(discount));

        paymentExpirationService.expireIfExpired(1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EXPIRED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(userCoupon.getUsedAt()).isNull();

        then(seatCacheService).should().syncAvailableSeatsAfterCommit(List.of(seat));
        then(seatCacheService).should().updateUserPurchaseLimit(event, "user01", 1, "SUB");
    }

    @Test
    @DisplayName("이미 완료된 결제는 무시한다")
    void expire_if_expired_ignores_paid_payment() {
        Payment payment = payment(reservation(event()), PaymentStatus.PAID, LocalDateTime.of(2020, 1, 1, 0, 0));

        given(paymentJpaRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));

        paymentExpirationService.expireIfExpired(1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
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

    private Reservation reservation(Event event) {
        return Reservation.builder()
                .reservationId(1L)
                .orderId("order-1")
                .userId("user01")
                .event(event)
                .status(ReservationStatus.PENDING_PAYMENT)
                .tickets(new ArrayList<>())
                .reservedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .build();
    }

    private Payment payment(Reservation reservation, PaymentStatus status, LocalDateTime expiresAt) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(status)
                .amount(180000)
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

    private UserCoupon userCoupon(LocalDateTime expiresAt) {
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId("user01")
                .status(UserCouponStatus.ISSUED)
                .issuedAt(LocalDateTime.of(2026, 7, 1, 0, 0))
                .expiresAt(expiresAt)
                .build();
        userCoupon.use(LocalDateTime.of(2026, 7, 27, 12, 0));
        return userCoupon;
    }

    private ReservationDiscount reservationDiscount(Reservation reservation, UserCoupon userCoupon) {
        return ReservationDiscount.builder()
                .reservation(reservation)
                .userCoupon(userCoupon)
                .discountType(DiscountType.COUPON)
                .discountName("할인 쿠폰")
                .discountAmount(10000)
                .build();
    }
}
