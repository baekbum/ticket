package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
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
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.refund.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessGatewayAttempt;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundRequestContext;
import dev.bum.ticket_service.service.payment.refund.RefundAction;
import dev.bum.ticket_service.service.payment.refund.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator.CancellationRefundCalculation;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

/** 사용자·관리자 공통 취소 절차. 호출자가 예매를 잠근 A 트랜잭션에 참여한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ReservationCancellationService {

    private final Clock clock;
    private final SeatCacheService seatCacheService;
    private final TicketPurchaseCountService ticketPurchaseCountService;
    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentRefundProcessService paymentRefundProcessService;
    private final CardPaymentRefundService cardPaymentRefundService;
    private final VirtualAccountPaymentRefundService virtualAccountPaymentRefundService;
    private final TicketJpaRepository ticketJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;

    public CancelReservationResponse cancel(Reservation reservation, CancelReservationRequest request) {
        validateCancelableReservation(reservation);

        List<Ticket> activeTickets = selectActiveTickets(ticketJpaRepository.findByReservation(reservation));
        List<Ticket> selectedTickets = selectTicketsForCancel(activeTickets, request.getSelectedTicketIdList());
        CancellationTicketValidator.validate(reservation, selectedTickets);
        boolean fullCancellation = isFullCancellation(activeTickets, selectedTickets);

        Payment payment = paymentJpaRepository.findByReservation(reservation)
                .orElseThrow(() -> new IllegalStateException("예매의 결제 정보를 찾을 수 없습니다."));

        validatePaymentMethod(payment);
        RefundAccountRequest account = payment.getMethod() == PaymentMethod.BANK_TRANSFER
                ? request.getRefundAccount() : null;

        // 1·2. 예매 잠금 아래 기존 미완료 요청을 찾고 대상 티켓·계좌가 동일한지 확인한다.
        PaymentRefundRequestContext requestContext = paymentRefundProcessService.inspectRequest(
                payment, selectedTickets, fullCancellation, account
        );

        // 3. 입금 전 무통장은 전체 취소만 허용하며, PG 환불 없이 로컬 취소 후 응답한다.
        if (isWaitingDeposit(payment, reservation, activeTickets)) {
            validateUnpaidCancellation(payment, reservation, fullCancellation, requestContext);
            applyLocalCancellation(reservation, selectedTickets, fullCancellation, false);

            return CancelReservationResponse.canceledWithoutGateway(
                    reservation.getReservationId(), selectedTickets.stream().map(Ticket::getTicketId).toList()
            );
        }

        validatePaidCancellation(payment, reservation, activeTickets);

        // 4·5. 동일 요청은 최초 조건으로 재개하고, 변경 요청은 마감·기존 PG 결과를 확인한다.
        PaymentRefundProcessGatewayAttempt attempt = paymentRefundProcessService.startGatewayAttempt(
                payment, selectedTickets, fullCancellation, account, requestContext
        );
        Long processId = attempt.getPaymentRefundProcessId();

        return switch (attempt.getAction()) {
            case RECOVER_PREVIOUS_REQUEST -> {
                // 기존 PG 성공 건만 로컬에 복구한다. 이번에 변경한 취소 대상은 실행하지 않는다.
                yield recoverPreviousRequest(processId);
            }
            case REQUEST_GATEWAY -> {
                refundGateway(payment, account, attempt);
                yield completeLocalCancellation(reservation, payment, selectedTickets, fullCancellation, attempt);
            }
            case APPLY_LOCAL -> {
                // PG 성공이 확인된 요청은 PG 재호출 없이 로컬 처리만 진행한다.
                yield completeLocalCancellation(reservation, payment, selectedTickets, fullCancellation, attempt);
            }
        };
    }

    private CancelReservationResponse completeLocalCancellation(
            Reservation reservation, Payment payment, List<Ticket> selectedTickets,
            boolean fullCancellation, PaymentRefundProcessGatewayAttempt attempt
    ) {
        Long processId = attempt.getPaymentRefundProcessId();
        CancellationRefundCalculation calculation = attempt.getCalculation();

        try {
            if (attempt.getAction() == RefundAction.APPLY_LOCAL) {
                applyPaymentRefund(payment, calculation.refundAmount(), calculation.cancellationFeeAmount());
            } else {
                payment.applyCancellationFee(calculation.cancellationFeeAmount());
            }

            applyLocalCancellation(
                    reservation, selectedTickets, fullCancellation,
                    fullCancellation || calculation.restoreCoupon()
            );
            // 로컬 취소가 끝난 뒤 성공 이력과 완료 상태를 A에서 함께 저장한다.
            paymentRefundProcessService.completeCancellation(
                    processId, payment, selectedTickets, calculation.refundAmount(), fullCancellation
            );
            return CancelReservationResponse.canceled(
                    reservation.getReservationId(), processId,
                    selectedTickets.stream().map(Ticket::getTicketId).toList()
            );
        } catch (RuntimeException e) {
            paymentRefundProcessService.markLocalFailed(processId, e);
            throw e;
        }
    }

    private void validatePaymentMethod(Payment payment) {
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER && payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("지원하지 않는 환불 수단입니다.");
        }
    }

    private boolean isWaitingDeposit(Payment payment, Reservation reservation, List<Ticket> activeTickets) {
        return payment.getMethod() == PaymentMethod.BANK_TRANSFER
                && reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
                && activeTickets.stream().allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING_PAYMENT);
    }

    private void validateUnpaidCancellation(
            Payment payment, Reservation reservation, boolean fullCancellation,
            PaymentRefundRequestContext requestContext
    ) {
        if (payment.getStatus() != PaymentStatus.WAITING_DEPOSIT) {
            throw new IllegalStateException("결제 대기 티켓과 결제 상태가 일치하지 않습니다.");
        }
        if (requestContext.hasPreviousRequest()) {
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

    private CancelReservationResponse recoverPreviousRequest(Long processId) {
        try {
            return paymentRefundProcessService.recoverPreviousRequest(processId);
        } catch (RuntimeException e) {
            paymentRefundProcessService.markLocalFailed(processId, e);
            throw e;
        }
    }

    private void refundGateway(Payment payment, RefundAccountRequest account, PaymentRefundProcessGatewayAttempt attempt) {
        Long processId = attempt.getPaymentRefundProcessId();
        int refundAmount = attempt.getCalculation().refundAmount();
        boolean fullPaymentRefund = refundAmount == payment.getRefundableAmount();

        // 결제 수단은 명시적으로 분기하고, PG 실패를 로컬 반영 실패와 구분해 기록한다.
        try {
            if (payment.getMethod() == PaymentMethod.CREDIT_CARD) {
                if (fullPaymentRefund) {
                    cardPaymentRefundService.refundAll(payment, processId);
                } else {
                    cardPaymentRefundService.refundPartial(payment, refundAmount, processId);
                }
            } else {
                if (fullPaymentRefund) {
                    virtualAccountPaymentRefundService.refundAll(payment, account, processId);
                } else {
                    virtualAccountPaymentRefundService.refundPartial(payment, refundAmount, account, processId);
                }
            }
            paymentRefundProcessService.markGatewaySucceeded(processId);
        } catch (RuntimeException e) {
            paymentRefundProcessService.markGatewayFailed(processId, e);
            throw e;
        }
    }

    private void applyPaymentRefund(Payment payment, int refundAmount, int cancellationFeeAmount) {
        if (refundAmount == 0 && cancellationFeeAmount == 0) return;
        payment.applyCancellation(refundAmount, cancellationFeeAmount);
    }

    private void applyLocalCancellation(
            Reservation reservation, List<Ticket> selectedTickets, boolean fullCancellation, boolean restoreCoupon
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

        seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats);
    }

    private void validateCancelableReservation(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new IllegalArgumentException("이미 취소되었거나 만료된 예매입니다.");
        }
    }

    private boolean isFullCancellation(List<Ticket> activeTickets, List<Ticket> selectedTickets) {
        return selectedTickets.size() == activeTickets.size();
    }

    private List<Ticket> selectActiveTickets(List<Ticket> tickets) {
        return tickets.stream()
                .filter(this::isActiveTicket)
                .toList();
    }

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

    private boolean isActiveTicket(Ticket ticket) {
        return ticket.getStatus() == TicketStatus.PENDING_PAYMENT || ticket.getStatus() == TicketStatus.PAID;
    }

    private List<Seat> cancelTickets(List<Ticket> selectedTickets) {
        selectedTickets.forEach(ticket -> {
            ticket.cancel();
            ticket.getSeat().available();
        });

        return selectedTickets.stream()
                .map(Ticket::getSeat)
                .toList();
    }

    private void restoreUsedCoupons(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        for (ReservationDiscount discount : discounts) {
            if (discount.getUserCoupon() != null && discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(now);
            }
        }
    }

}
