package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExpirationService {

    private final PaymentJpaRepository paymentJpaRepository;
    private final TicketRepository ticketRepository;
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final SeatCacheService seatCacheService;

    /**
     * 만료 대상 결제를 락으로 재조회한 뒤 결제, 예매, 티켓, 좌석, 배송, 쿠폰 상태를 함께 정리한다.
     */
    @Transactional
    public void expireIfExpired(Long paymentId) {
        Payment payment = paymentJpaRepository.findByIdForUpdate(paymentId)
                .orElse(null);
        if (payment == null || !isExpiredIncompletePayment(payment, LocalDateTime.now())) {
            return;
        }

        expire(payment);
    }

    public void expire(Payment payment) {
        Reservation reservation = payment.getReservation();
        List<Ticket> tickets = ticketRepository.selectByReservation(reservation);
        List<Seat> availableSeats = tickets.stream()
                .map(Ticket::getSeat)
                .toList();

        payment.expire();
        reservation.expire();
        tickets.forEach(ticket -> {
            ticket.expire();
            ticket.getSeat().available();
        });
        reservationDeliveryJpaRepository.findByReservation(reservation)
                .ifPresent(ReservationDelivery::cancel);
        restoreUsedCoupons(reservation);

        seatCacheService.syncAvailableSeatsAfterCommit(availableSeats);

        log.info("[PAYMENT][EXPIRE][HANDLE] paymentId={}, reservationId={}, seatCount={}",
                payment.getPaymentId(), reservation.getReservationId(), availableSeats.size());
    }

    private boolean isExpiredIncompletePayment(Payment payment, LocalDateTime now) {
        return (payment.getStatus() == PaymentStatus.READY || payment.getStatus() == PaymentStatus.WAITING_DEPOSIT)
                && payment.getExpiresAt() != null
                && !payment.getExpiresAt().isAfter(now);
    }

    private void restoreUsedCoupons(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now();
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        for (ReservationDiscount discount : discounts) {
            if (discount.getUserCoupon() != null && discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(now);
            }
        }
    }
}
