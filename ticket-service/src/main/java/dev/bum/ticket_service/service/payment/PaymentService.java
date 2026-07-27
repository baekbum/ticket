package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.CardPaymentApproveRequest;
import dev.bum.common.service.ticket.payment.dto.CompletePaymentRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
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
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
    private final QueueAccessService queueAccessService;
    private final MockCardAuthorizationService mockCardAuthorizationService;

    /**
     * PG 승인 또는 무통장 입금 확인 이후 결제를 최종 완료 처리한다.
     * 결제, 예약, 티켓, 좌석 상태를 같은 트랜잭션에서 확정하고 커밋 후 후속 이벤트를 발행한다.
     */
    @AuditLog(action = "PAYMENT_CONFIRM", targetType = "PAYMENT")
    public PaymentResponse confirm(CompletePaymentRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNo(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("해당 결제 정보가 존재하지 않습니다."));

        return completePayment(payment, request.getPaidAt());
    }

    @AuditLog(action = "CARD_PAYMENT_APPROVE", targetType = "PAYMENT")
    public PaymentResponse approveCard(String currentUserId, String queueToken, CardPaymentApproveRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNo(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        Reservation reservation = payment.getReservation();

        validatePaymentOwner(currentUserId, reservation);
        queueAccessService.validate(resolveEventId(reservation), currentUserId, queueToken);
        validateCardPaymentReady(payment);

        if (!mockCardAuthorizationService.approve(request)) {
            throw new IllegalArgumentException("카드 정보가 일치하지 않습니다.");
        }

        return completePayment(payment, null);
    }

    private PaymentResponse completePayment(Payment payment, LocalDateTime paidAt) {
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

        payment.complete(paidAt);
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

    private void validatePaymentOwner(String currentUserId, Reservation reservation) {
        if (!StringUtils.hasText(currentUserId)) {
            throw new AccessDeniedException("사용자 인증 정보가 필요합니다.");
        }
        if (reservation == null || !currentUserId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("다른 사용자의 결제 요청입니다.");
        }
    }

    private Long resolveEventId(Reservation reservation) {
        if (reservation == null || reservation.getEvent() == null || reservation.getEvent().getEventId() == null) {
            throw new IllegalArgumentException("대기열 검증을 위한 이벤트 정보가 없습니다.");
        }
        return reservation.getEvent().getEventId();
    }

    private void validateCardPaymentReady(Payment payment) {
        if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("카드 결제 요청이 아닙니다.");
        }
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalArgumentException("카드 승인 처리할 수 없는 결제 상태입니다.");
        }
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
