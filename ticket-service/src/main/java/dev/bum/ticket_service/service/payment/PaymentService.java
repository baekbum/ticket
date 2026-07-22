package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.CompletePaymentRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.kafka.payment.PaymentEventProducer;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentJpaRepository paymentJpaRepository;
    private final TicketRepository ticketRepository;
    private final SeatCacheService seatCacheService;
    private final PaymentEventProducer paymentEventProducer;

    /**
     * PG 승인 또는 무통장 입금 확인 이후 결제를 최종 완료 처리한다.
     * 결제, 예약, 티켓, 좌석 상태를 같은 트랜잭션에서 확정하고 커밋 후 후속 이벤트를 발행한다.
     */
    @AuditLog(action = "PAYMENT_CONFIRM", targetType = "PAYMENT")
    public PaymentResponse confirm(CompletePaymentRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNo(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("해당 결제 정보가 존재하지 않습니다."));

        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment.toResponse();
        }
        if (payment.getStatus() != PaymentStatus.READY && payment.getStatus() != PaymentStatus.WAITING_DEPOSIT) {
            throw new IllegalArgumentException("결제 완료 처리할 수 없는 상태입니다.");
        }
        PaymentStatus beforePaymentStatus = payment.getStatus();

        Reservation reservation = payment.getReservation();
        List<Ticket> tickets = ticketRepository.selectByReservation(reservation);
        List<Seat> seats = tickets.stream()
                .map(Ticket::getSeat)
                .collect(Collectors.toList());

        payment.complete(request.getPaidAt());
        AuditDataMapper.setFieldChange("status", beforePaymentStatus, payment.getStatus());
        reservation.paid();
        for (Ticket ticket : tickets) {
            ticket.paid();
            ticket.getSeat().reserved();
        }

        String paymentNo = payment.getPaymentNo();
        Long reservationId = reservation.getReservationId();
        String orderId = reservation.getOrderId();
        Integer amount = payment.getAmount();

        seatCacheService.syncReservedSeatsAfterCommit(seats);
        runAfterCommit(() -> paymentEventProducer.sendPaymentCompleted(paymentNo, reservationId, orderId, amount));

        return payment.toResponse();
    }

    /**
     * DB 트랜잭션 커밋이 성공한 뒤에만 외부 부수 효과를 실행한다.
     * 트랜잭션이 없을 때는 호출 위치에서 즉시 실행한다.
     */
    private void runAfterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 결제 트랜잭션 커밋 이후 결제 완료 이벤트를 발행한다.
             */
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}
