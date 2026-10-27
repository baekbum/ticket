package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.reservation.policy.ReservationCancellationPolicy;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator;
import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator.CancellationRefundCalculation;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** B 트랜잭션: 새 환불 프로세스와 최초 스냅샷 생성만 담당한다. 기존 프로세스는 수정하지 않는다. */
@Service
@RequiredArgsConstructor
public class PaymentRefundProcessCreator {
    private final PaymentRefundProcessJpaRepository processes;
    private final TicketJpaRepository tickets;
    private final ReservationDiscountJpaRepository discounts;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createNewRequest(
            Payment payment, List<Ticket> selectedTickets, boolean full,
            RefundAccountRequest account, LocalDateTime requestedAt) {

        ReservationCancellationPolicy.validateNewRequest(
                requestedAt, payment.getReservation().getEvent() == null
                        ? null : payment.getReservation().getEvent().getCancelDeadlineAt()
        );

        List<Ticket> active = tickets.findByReservation(payment.getReservation()).stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.PENDING_PAYMENT)
                .toList();

        CancellationRefundCalculation calculation = CancellationFeeCalculator.calculate(
                payment.getReservation(), payment, active, selectedTickets,
                discounts.findByReservation(payment.getReservation()), full, requestedAt
        );

        return persist(payment, selectedTickets, full, account, calculation, requestedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long copyOriginalRequest(
            Payment payment, List<Ticket> selectedTickets, boolean full, RefundAccountRequest account,
            CancellationRefundCalculation calculation, LocalDateTime capturedAt) {

        return persist(payment, selectedTickets, full, account, calculation, capturedAt);
    }

    private Long persist(
            Payment payment, List<Ticket> selectedTickets, boolean full, RefundAccountRequest account,
            CancellationRefundCalculation calculation, LocalDateTime capturedAt) {

        PaymentRefundProcess process = PaymentRefundProcess.create(
                payment, selectedTickets, calculation.refundAmount(), calculation.cancellationFeeAmount(), full,
                account, capturedAt, calculation.restoreCoupon()
        );

        if (calculation.refundAmount() == 0) process.gatewaySucceeded();

        processes.save(process);

        return process.getPaymentRefundProcessId();
    }
}
