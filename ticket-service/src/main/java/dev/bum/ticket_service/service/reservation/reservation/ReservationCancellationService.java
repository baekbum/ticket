package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.reservation.policy.ReservationCancellationPolicy;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountCancelOutboxService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;

import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;

/** 사용자·관리자 공통 취소 절차. 호출자가 예매를 잠근 A 트랜잭션에 참여한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ReservationCancellationService {

    // 처리 시각을 제공하는 시계.
    private final Clock clock;
    // 좌석 캐시 업무 처리를 담당하는 서비스.
    private final SeatCacheService seatCacheService;
    // 티켓 구매 수량 업무 처리를 담당하는 서비스.
    private final TicketPurchaseCountService ticketPurchaseCountService;
    // 결제 데이터를 조회하고 저장하는 레포지토리.
    private final PaymentJpaRepository paymentJpaRepository;
    // 결제 환불 처리 업무 처리를 담당하는 서비스.
    private final PaymentRefundProcessService paymentRefundProcessService;
    // 티켓 데이터를 조회하고 저장하는 레포지토리.
    private final TicketJpaRepository ticketJpaRepository;
    // 예매 할인 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    // 사용자 쿠폰 데이터를 조회하고 저장하는 레포지토리.
    private final UserCouponRepository userCouponRepository;
    // 엔티티의 영속 상태와 데이터베이스 잠금을 관리하는 도구.
    private final EntityManager entityManager;
    // 가상계좌 취소 Outbox 이벤트 업무 처리를 담당하는 서비스.
    private final VirtualAccountCancelOutboxService virtualAccountCancelOutboxService;

    /**
     * 예매를 취소 상태로 변경한다.
     */
    public CancelReservationResponse cancel(Reservation reservation, CancelReservationRequest request) {

        validateCancelableReservation(reservation);

        List<Ticket> activeTickets = selectActiveTickets(ticketJpaRepository.findByReservation(reservation));
        List<Ticket> selectedTickets = selectTicketsForCancel(activeTickets, request.getSelectedTicketIdList());
        CancellationTicketValidator.validate(reservation, selectedTickets);
        boolean fullCancellation = isFullCancellation(activeTickets, selectedTickets);

        Payment payment = paymentJpaRepository.findByReservation(reservation)
                .orElseThrow(() -> new IllegalStateException("예매의 결제 정보를 찾을 수 없습니다."));

        validatePaymentMethod(payment);
        boolean hasUnfinishedRefund = paymentRefundProcessService.hasUnfinishedRequest(payment);

        // 입금 전 무통장은 전체 취소만 허용하며, PG 환불 없이 로컬 취소 후 응답한다.
        if (isWaitingDeposit(payment, reservation, activeTickets)) {
            // 예약은 호출자가 이미 잠갔다. 입금과 예약 → 결제 순서를 맞추고 구매 매수 → 티켓 → 좌석도 잠근다.
            entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
            validateUnpaidCancellation(payment, reservation, fullCancellation, hasUnfinishedRefund);
            ticketPurchaseCountService.prepareStatusAdjustment(reservation, activeTickets);
            activeTickets.stream().map(Ticket::getSeat).distinct()
                    .sorted(Comparator.comparing(Seat::getSeatId))
                    .forEach(seat -> entityManager.refresh(seat, LockModeType.PESSIMISTIC_WRITE));
            CancellationTicketValidator.validate(reservation, selectedTickets);
            validateUnpaidCancellation(payment, reservation, fullCancellation, hasUnfinishedRefund);
            applyLocalCancellation(reservation, selectedTickets, fullCancellation, false);
            payment.cancel();
            virtualAccountCancelOutboxService.enqueue(payment);

            return CancelReservationResponse.canceledWithoutGateway(
                    reservation.getReservationId(), selectedTickets.stream().map(Ticket::getTicketId).toList()
            );
        }

        validatePaidCancellation(payment, reservation, activeTickets);

        throw new IllegalStateException("결제 완료된 예매의 환불은 환불 신청 API를 이용해주세요.");
    }

    /**
     * 요청에 사용할 수 있는 결제 수단인지 검증한다.
     */
    private void validatePaymentMethod(Payment payment) {

        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER && payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("지원하지 않는 환불 수단입니다.");
        }
    }

    /**
     * 가상계좌 입금 대기 상태인지 확인한다.
     */
    private boolean isWaitingDeposit(Payment payment, Reservation reservation, List<Ticket> activeTickets) {

        return payment.getMethod() == PaymentMethod.BANK_TRANSFER
                && reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
                && activeTickets.stream().allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING_PAYMENT);
    }

    /**
     * 환불 신청 API를 안내하기 전에 기존 예매·티켓·결제 상태의 불일치를 거절한다.
     */
    private void validatePaidCancellation(Payment payment, Reservation reservation, List<Ticket> activeTickets) {

        if ((reservation.getStatus() != ReservationStatus.PAID
                && reservation.getStatus() != ReservationStatus.PARTIALLY_CANCELLED)
                || activeTickets.stream().anyMatch(ticket -> ticket.getStatus() != TicketStatus.PAID)) {
            throw new IllegalStateException("예매 상태와 티켓의 결제 상태가 일치하지 않습니다.");
        }

        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("결제 완료 티켓과 결제 상태가 일치하지 않습니다.");
        }
    }

    /**
     * 미결제 예매가 취소 가능한지 검증한다.
     */
    private void validateUnpaidCancellation(
            Payment payment,
            Reservation reservation,
            boolean fullCancellation,
            boolean hasUnfinishedRefund
    ) {

        if (payment.getStatus() != PaymentStatus.WAITING_DEPOSIT
                && !(payment.getStatus() == PaymentStatus.READY && payment.getGatewayBankCompany() != null)) {
            throw new IllegalStateException("결제 대기 티켓과 결제 상태가 일치하지 않습니다.");
        }

        if (hasUnfinishedRefund) {
            throw new IllegalStateException("입금 대기 예매에 진행 중인 환불 요청이 존재합니다.");
        }

        if (!fullCancellation) {
            throw new IllegalArgumentException("입금 전 무통장 예매는 전체 취소만 가능합니다.");
        }

        ReservationCancellationPolicy.validateNewRequest(
                LocalDateTime.now(clock),
                reservation.getEvent() == null ? null : reservation.getEvent().getCancelDeadlineAt()
        );
    }

    /**
     * 취소 결과를 예매, 티켓과 결제에 반영한다.
     */
    private void applyLocalCancellation(
            Reservation reservation,
            List<Ticket> selectedTickets,
            boolean fullCancellation,
            boolean restoreCoupon
    ) {

        ticketPurchaseCountService.release(reservation, selectedTickets); // 매수 차감 원복
        List<Seat> cancelledSeats = cancelTickets(selectedTickets); // 티켓, 좌석 취소

        // 예매 상태 변경
        if (fullCancellation) {
            reservation.cancel();
        } else {
            reservation.partial_cancel();
        }

        // 쿠폰 원복
        if (restoreCoupon) restoreUsedCoupons(reservation);

        seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats, reservation.getUserId(), reservation.getOrderId());
    }

    /**
     * 현재 예매가 취소 가능한 상태와 기간인지 검증한다.
     */
    private void validateCancelableReservation(Reservation reservation) {

        if (reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new IllegalArgumentException("이미 취소되었거나 만료된 예매입니다.");
        }
    }

    /**
     * 전체 취소 여부 여부를 확인한다.
     */
    private boolean isFullCancellation(List<Ticket> activeTickets, List<Ticket> selectedTickets) {

        return selectedTickets.size() == activeTickets.size();
    }

    /**
     * 취소되지 않은 유효 티켓을 조회한다.
     */
    private List<Ticket> selectActiveTickets(List<Ticket> tickets) {

        return tickets.stream()
                .filter(this::isActiveTicket)
                .toList();
    }

    /**
     * 취소 요청에 해당하는 티켓을 조회한다.
     */
    private List<Ticket> selectTicketsForCancel(List<Ticket> activeTickets, List<Long> selectedTicketIdList) {

        if (selectedTicketIdList == null || selectedTicketIdList.isEmpty()) {
            throw new IllegalArgumentException("취소할 티켓을 선택해야 합니다.");
        }

        if (selectedTicketIdList.size() != new HashSet<>(selectedTicketIdList).size()) {
            throw new IllegalArgumentException("취소할 티켓이 중복 선택되었습니다.");
        }

        List<Ticket> selectedTickets = activeTickets.stream()
                .filter(ticket -> selectedTicketIdList.contains(ticket.getTicketId()))
                .toList();

        if (selectedTickets.size() != selectedTicketIdList.size()) {
            throw new IllegalArgumentException("선택한 티켓 중 취소 가능한 예매 티켓이 아닌 항목이 있습니다.");
        }

        return selectedTickets;
    }

    /**
     * 티켓이 유효한 예매 상태인지 확인한다.
     */
    private boolean isActiveTicket(Ticket ticket) {

        return ticket.getStatus() == TicketStatus.PENDING_PAYMENT || ticket.getStatus() == TicketStatus.PAID;
    }

    /**
     * 예매 처리에서 처리 대상 티켓을 취소한다.
     */
    private List<Seat> cancelTickets(List<Ticket> selectedTickets) {

        selectedTickets.forEach(ticket -> {
            ticket.cancel();
            ticket.getSeat().available();
        });

        return selectedTickets.stream()
                .map(Ticket::getSeat)
                .toList();
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
                discount.getUserCoupon().restore(LocalDateTime.now(clock));
            }
        }
    }
}
