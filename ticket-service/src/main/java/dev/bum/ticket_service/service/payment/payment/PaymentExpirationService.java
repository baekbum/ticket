package dev.bum.ticket_service.service.payment.payment;

import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;
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

    // 결제 데이터를 조회하고 저장하는 레포지토리.
    private final PaymentJpaRepository paymentJpaRepository;
    // 티켓 데이터를 조회하고 저장하는 레포지토리.
    private final TicketRepository ticketRepository;
    // 예매 배송 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;
    // 예매 할인 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    // 사용자 쿠폰 데이터를 조회하고 저장하는 레포지토리.
    private final UserCouponRepository userCouponRepository;
    // 좌석 캐시 업무 처리를 담당하는 서비스.
    private final SeatCacheService seatCacheService;
    // 티켓 구매 수량 업무 처리를 담당하는 서비스.
    private final TicketPurchaseCountService ticketPurchaseCountService;

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

    /**
     * 결제를 만료 상태로 변경한다.
     */
    public void expire(Payment payment) {

        Reservation reservation = payment.getReservation();
        List<Ticket> tickets = ticketRepository.selectByReservation(reservation);
        List<Seat> availableSeats = tickets.stream()
                .map(Ticket::getSeat)
                .toList();

        ticketPurchaseCountService.release(reservation, tickets);
        payment.expire();
        reservation.expire();
        tickets.forEach(ticket -> {
            ticket.expire();
            ticket.getSeat().available();
        });
        reservationDeliveryJpaRepository.findByReservation(reservation)
                .ifPresent(ReservationDelivery::cancel);
        restoreUsedCoupons(reservation);

        seatCacheService.syncAvailableSeatsAfterCommit(availableSeats, reservation.getUserId(), reservation.getOrderId());

        log.info("[PAYMENT][EXPIRE][HANDLE] paymentId={}, reservationId={}, seatCount={}",
                payment.getPaymentId(), reservation.getReservationId(), availableSeats.size());
    }

    /**
     * 완료되지 않은 결제가 만료되었는지 확인한다.
     */
    private boolean isExpiredIncompletePayment(Payment payment, LocalDateTime now) {

        // 입금 알림이 지연됐을 수 있으므로 발급된 무통장 계좌의 만료는 PG 이벤트로 확정한다.
        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER
                && payment.getStatus() == PaymentStatus.WAITING_DEPOSIT) return false;
        return (payment.getStatus() == PaymentStatus.READY || payment.getStatus() == PaymentStatus.WAITING_DEPOSIT)
                && payment.getExpiresAt() != null
                && !payment.getExpiresAt().isAfter(now);
    }

    /**
     * 취소한 예매에 사용된 쿠폰을 복원한다.
     */
    private void restoreUsedCoupons(Reservation reservation) {

        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        for (ReservationDiscount discount : discounts) {
            if (discount.getUserCoupon() == null) continue;

            userCouponRepository.lockForUpdate(discount.getUserCoupon());
            if (discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(LocalDateTime.now());
            }
        }
    }
}
