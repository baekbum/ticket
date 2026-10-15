package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessCondRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistory;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistoryJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.reservation.reservation.CancellationTicketValidator;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentRefundProcessService {

    private final PaymentRefundProcessJpaRepository paymentRefundProcessJpaRepository;
    private final PaymentRefundHistoryJpaRepository paymentRefundHistoryJpaRepository;
    private final ReservationJpaRepository reservationJpaRepository;
    private final TicketJpaRepository ticketJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final SeatCacheService seatCacheService;
    private final TicketPurchaseCountService ticketPurchaseCountService;

    @Transactional(readOnly = true)
    public CustomPageResponse<PaymentRefundProcessResponse> selectByCond(
            PaymentRefundProcessCondRequest cond) {
        PaymentRefundProcessCondRequest normalizedCond =
                cond != null ? cond : new PaymentRefundProcessCondRequest();
        PageRequest pageable = PageRequest.of(
                normalizedCond.getPage() != null ? normalizedCond.getPage() : 0,
                normalizedCond.getSize() != null ? normalizedCond.getSize() : 10,
                makeSort(normalizedCond.getSort())
        );

        Page<PaymentRefundProcessResponse> page = paymentRefundProcessJpaRepository
                .findAll(makeSpec(normalizedCond), pageable)
                .map(PaymentRefundProcess::toResponse);

        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount) {
        return create(payment, selectedTickets, refundAmount, 0, fullCancellation, refundAccount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            int cancellationFeeAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount) {
        PaymentRefundProcess process = paymentRefundProcessJpaRepository.save(
                PaymentRefundProcess.create(
                        payment,
                        selectedTickets,
                        refundAmount,
                        cancellationFeeAmount,
                        fullCancellation,
                        refundAccount
                )
        );
        return process.getPaymentRefundProcessId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRefundProcessGatewayAttempt startGatewayAttempt(
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount) {
        return startGatewayAttempt(
                payment, selectedTickets, refundAmount, 0, fullCancellation, refundAccount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRefundProcessGatewayAttempt startGatewayAttempt(
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            int cancellationFeeAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount) {
        return paymentRefundProcessJpaRepository
                .findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(
                        payment.getReservation(),
                        List.of(
                                PaymentRefundProcessStatus.REQUESTED,
                                PaymentRefundProcessStatus.GATEWAY_FAILED,
                                PaymentRefundProcessStatus.GATEWAY_SUCCEEDED,
                                PaymentRefundProcessStatus.LOCAL_FAILED))
                .map(
                        process ->
                                prepareGatewayAttempt(
                                        process,
                                        selectedTickets,
                                        refundAmount,
                                        cancellationFeeAmount,
                                        fullCancellation,
                                        refundAccount))
                .orElseGet(
                        () ->
                                new PaymentRefundProcessGatewayAttempt(
                                        create(
                                                payment,
                                                selectedTickets,
                                                refundAmount,
                                                cancellationFeeAmount,
                                                fullCancellation,
                                                refundAccount
                                        ),
                                        true,
                                        false));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGatewaySucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::gatewaySucceeded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGatewayFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .ifPresent(process -> process.gatewayFailed(messageOf(throwable)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLocalSucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::localSucceeded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLocalFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .ifPresent(process -> process.localFailed(messageOf(throwable)));
    }

    @Transactional
    public void savePaymentRefundHistory(
            Long paymentRefundProcessId,
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation) {

        if (paymentRefundProcessId == null) {
            return;
        }

        PaymentRefundProcess process = findById(paymentRefundProcessId);
        savePaymentRefundHistory(process, payment, selectedTickets, refundAmount, fullCancellation);
    }

    @AuditLog(
            action = "PAYMENT_REFUND_PROCESS_LOCAL_COMPLETE",
            targetType = "PAYMENT_REFUND_PROCESS")
    @Transactional
    public PaymentRefundProcessResponse completeLocal(Long paymentRefundProcessId) {
        Long reservationId = paymentRefundProcessJpaRepository.findReservationIdById(paymentRefundProcessId)
                .orElseThrow(() -> new IllegalArgumentException("환불 처리 현황을 찾을 수 없습니다."));
        // 프로세스나 티켓을 읽기 전에 사용자·관리자 취소와 같은 예매 행을 잠근다.
        reservationJpaRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예매 정보를 찾을 수 없습니다."));
        PaymentRefundProcess process = findById(paymentRefundProcessId);
        if (process.getStatus() != PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
                && process.getStatus() != PaymentRefundProcessStatus.LOCAL_FAILED) {
            throw new IllegalArgumentException(
                    "gateway 환불 성공 이후 로컬 반영이 완료되지 않은 환불 처리만 수동 완료 처리할 수 있습니다.");
        }

        PaymentRefundProcessStatus beforeStatus = process.getStatus();
        completeLocal(process, true);
        AuditDataMapper.setFieldChange("status", beforeStatus, process.getStatus());
        return process.toResponse();
    }

    private String messageOf(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        return throwable.getMessage() != null
                ? throwable.getMessage()
                : throwable.getClass().getSimpleName();
    }

    private PaymentRefundProcess findById(Long paymentRefundProcessId) {
        return paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .orElseThrow(() -> new IllegalArgumentException("환불 처리 현황을 찾을 수 없습니다."));
    }

    private PaymentRefundProcessGatewayAttempt prepareGatewayAttempt(
            PaymentRefundProcess process,
            List<Ticket> selectedTickets,
            int refundAmount,
            int cancellationFeeAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount) {
        validateSameRefundProcess(
                process, selectedTickets, refundAmount, cancellationFeeAmount, fullCancellation);
        process.validateRefundAccount(refundAccount);

        if (process.getStatus() == PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
                || process.getStatus() == PaymentRefundProcessStatus.LOCAL_FAILED) {
            return new PaymentRefundProcessGatewayAttempt(
                    process.getPaymentRefundProcessId(), false, true);
        }

        process.gatewayRetryRequested();
        return new PaymentRefundProcessGatewayAttempt(
                process.getPaymentRefundProcessId(), true, false);
    }

    private void validateSameRefundProcess(
            PaymentRefundProcess process,
            List<Ticket> selectedTickets,
            int refundAmount,
            int cancellationFeeAmount,
            boolean fullCancellation) {
        String selectedTicketIds = selectedTickets.stream()
                .map(Ticket::getTicketId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (!process.getRefundAmount().equals(refundAmount)
                || !process.getCancellationFeeAmount().equals(cancellationFeeAmount)
                || process.isFullCancellation() != fullCancellation
                || !process.getSelectedTicketIds().equals(selectedTicketIds)) {
            throw new IllegalArgumentException("진행 중인 환불 처리와 요청 정보가 일치하지 않습니다.");
        }
    }

    private void completeLocal(PaymentRefundProcess process, boolean applyPaymentRefund) {
        try {
            Payment payment = process.getPayment();
            Reservation reservation = process.getReservation();
            List<Ticket> selectedTickets = selectProcessTickets(process, reservation);
            CancellationTicketValidator.validate(reservation, selectedTickets);
            if (selectedTickets.isEmpty()
                    || selectedTickets.stream().anyMatch(ticket -> ticket.getStatus() != TicketStatus.PAID)) {
                throw new IllegalArgumentException("결제 완료 상태인 티켓만 환불 완료 처리할 수 있습니다.");
            }
            if (applyPaymentRefund) {
                applyPaymentRefund(payment, process);
            }
            boolean restoreCouponOnCancel = process.isFullCancellation()
                    && reservation.getStatus() == ReservationStatus.PAID;

            ticketPurchaseCountService.release(reservation, selectedTickets);
            List<Seat> cancelledSeats = cancelTickets(selectedTickets);
            applyReservationCancelStatus(
                    reservation, process.isFullCancellation(), restoreCouponOnCancel);
            savePaymentRefundHistory(
                    process,
                    payment,
                    selectedTickets,
                    process.getRefundAmount(),
                    process.isFullCancellation());
            process.localSucceeded();

            seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats);
        } catch (RuntimeException e) {
            process.localFailed(messageOf(e));
            throw e;
        }
    }

    private void applyPaymentRefund(Payment payment, PaymentRefundProcess process) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.PAID
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            payment.applyCancellation(
                    process.getRefundAmount(), process.getCancellationFeeAmount());
        }
    }

    private void savePaymentRefundHistory(
            PaymentRefundProcess process,
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation) {
        if (paymentRefundHistoryJpaRepository.existsByPaymentRefundProcess(process)) {
            throw new IllegalStateException("이미 환불 이력이 저장된 환불 처리입니다.");
        }

        paymentRefundHistoryJpaRepository.save(
                PaymentRefundHistory.create(
                        process, payment, selectedTickets, refundAmount, fullCancellation));
        // 본문 실행 중에는 독립 실패 기록 트랜잭션과 잠금이 충돌하지 않도록 완료 상태를 커밋 직전에 반영한다.
        // 성공 이력과 완료 상태는 여전히 예매·결제 변경과 같은 트랜잭션으로 커밋된다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void beforeCommit(boolean readOnly) {
                            process.localSucceeded();
                        }
                    }
            );
        } else {
            process.localSucceeded();
        }
    }

    private List<Ticket> selectProcessTickets(
            PaymentRefundProcess process, Reservation reservation) {
        List<Long> ticketIds = parseTicketIds(process.getSelectedTicketIds());
        List<Ticket> tickets = ticketJpaRepository.findAllByTicketIdIn(ticketIds);
        if (tickets.size() != ticketIds.size()) {
            throw new IllegalArgumentException("환불 처리 대상 티켓을 찾을 수 없습니다.");
        }
        if (tickets.stream()
                .anyMatch(
                        ticket ->
                                !ticket.getReservation()
                                        .getReservationId()
                                        .equals(reservation.getReservationId()))) {
            throw new IllegalArgumentException("환불 처리 대상 티켓이 예매 정보와 일치하지 않습니다.");
        }
        return tickets;
    }

    private List<Long> parseTicketIds(String selectedTicketIds) {
        if (!StringUtils.hasText(selectedTicketIds)) {
            return List.of();
        }
        return Arrays.stream(selectedTicketIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
    }

    private List<Seat> cancelTickets(List<Ticket> selectedTickets) {
        selectedTickets.forEach(
                ticket -> {
                    if (ticket.getStatus() != TicketStatus.CANCELLED) {
                        ticket.cancel();
                    }
                    if (ticket.getSeat() != null) {
                        ticket.getSeat().available();
                    }
                });

        return selectedTickets.stream()
                .map(Ticket::getSeat)
                .filter(Objects::nonNull)
                .toList();
    }

    private void applyReservationCancelStatus(
            Reservation reservation, boolean fullCancellation, boolean restoreCouponOnCancel) {
        if (!fullCancellation) {
            reservation.partial_cancel();
            return;
        }

        reservation.cancel();
        if (restoreCouponOnCancel) {
            restoreUsedCoupons(reservation);
        }
    }

    private void restoreUsedCoupons(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now();
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        for (ReservationDiscount discount : discounts) {
            if (discount.getUserCoupon() != null
                    && discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(now);
            }
        }
    }

    private Specification<PaymentRefundProcess> makeSpec(PaymentRefundProcessCondRequest cond) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("refund", JoinType.LEFT)
                        .fetch("payment", JoinType.LEFT)
                        .fetch("reservation", JoinType.LEFT);
            }

            if (cond.getPaymentRefundProcessId() != null && cond.getPaymentRefundProcessId() > 0) {
                predicates.add(
                        cb.equal(
                                root.get("paymentRefundProcessId"),
                                cond.getPaymentRefundProcessId()));
            }
            if (cond.getReservationId() != null && cond.getReservationId() > 0) {
                predicates.add(
                        cb.equal(
                                root.get("refund")
                                        .get("payment")
                                        .get("reservation")
                                        .get("reservationId"),
                                cond.getReservationId()));
            }
            if (cond.getPaymentId() != null && cond.getPaymentId() > 0) {
                predicates.add(
                        cb.equal(
                                root.get("refund").get("payment").get("paymentId"),
                                cond.getPaymentId()));
            }
            if (StringUtils.hasText(cond.getPaymentNo())) {
                predicates.add(
                        cb.like(
                                root.get("refund").get("payment").get("paymentNo"),
                                "%" + cond.getPaymentNo() + "%"));
            }
            if (cond.getMethod() != null) {
                predicates.add(
                        cb.equal(
                                root.get("refund").get("payment").get("method"), cond.getMethod()));
            }
            if (StringUtils.hasText(cond.getStatus())) {
                predicates.add(
                        cb.equal(
                                root.get("status"),
                                PaymentRefundProcessStatus.valueOf(cond.getStatus())));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort makeSort(List<String> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "paymentRefundProcessId");
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (String sort : sorts) {
            String[] parts = sort.split("-");
            if (parts.length == 2) {
                String property =
                        switch (parts[0]) {
                            case "paymentId" -> "refund.payment.paymentId";
                            case "reservationId" -> "refund.payment.reservation.reservationId";
                            case "paymentNo", "method" -> "refund.payment." + parts[0];
                            case "refundAmount", "cancellationFeeAmount", "fullCancellation" ->
                                    "refund.snapshot." + parts[0];
                            default -> parts[0];
                        };
                orders.add(new Sort.Order(Sort.Direction.fromString(parts[1]), property));
            }
        }

        return orders.isEmpty()
                ? Sort.by(Sort.Direction.DESC, "paymentRefundProcessId")
                : Sort.by(orders);
    }
}
