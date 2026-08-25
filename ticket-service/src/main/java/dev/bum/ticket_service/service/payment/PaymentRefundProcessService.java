package dev.bum.ticket_service.service.payment;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessCondRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentRefundHistory;
import dev.bum.ticket_service.jpa.payment.PaymentRefundHistoryJpaRepository;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentRefundProcessService {

    private final PaymentRefundProcessJpaRepository paymentRefundProcessJpaRepository;
    private final PaymentRefundHistoryJpaRepository paymentRefundHistoryJpaRepository;
    private final TicketJpaRepository ticketJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final SeatCacheService seatCacheService;

    @Transactional(readOnly = true)
    public CustomPageResponse<PaymentRefundProcessResponse> selectByCond(PaymentRefundProcessCondRequest cond) {
        PaymentRefundProcessCondRequest normalizedCond = cond != null ? cond : new PaymentRefundProcessCondRequest();
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
                page.getTotalPages()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount
    ) {
        PaymentRefundProcess process = paymentRefundProcessJpaRepository.save(
                PaymentRefundProcess.create(payment, selectedTickets, refundAmount, fullCancellation, refundAccount)
        );
        return process.getPaymentRefundProcessId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRefundProcessGatewayAttempt startGatewayAttempt(
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount
    ) {
        return paymentRefundProcessJpaRepository.findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(
                        payment.getReservation(),
                        List.of(
                                PaymentRefundProcessStatus.REQUESTED,
                                PaymentRefundProcessStatus.GATEWAY_FAILED,
                                PaymentRefundProcessStatus.GATEWAY_SUCCEEDED,
                                PaymentRefundProcessStatus.LOCAL_FAILED
                        )
                )
                .map(process -> prepareGatewayAttempt(process, selectedTickets, refundAmount, fullCancellation, refundAccount))
                .orElseGet(() -> new PaymentRefundProcessGatewayAttempt(
                        create(payment, selectedTickets, refundAmount, fullCancellation, refundAccount),
                        true,
                        false
                ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGatewaySucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::gatewaySucceeded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGatewayFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(process -> process.gatewayFailed(messageOf(throwable)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLocalSucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::localSucceeded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLocalFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(process -> process.localFailed(messageOf(throwable)));
    }

    @Transactional
    public PaymentRefundProcessResponse completeLocal(Long paymentRefundProcessId) {
        PaymentRefundProcess process = findById(paymentRefundProcessId);
        if (process.getStatus() != PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
                && process.getStatus() != PaymentRefundProcessStatus.LOCAL_FAILED) {
            throw new IllegalArgumentException("gateway 환불 성공 이후 로컬 반영이 완료되지 않은 환불 처리만 수동 완료 처리할 수 있습니다.");
        }

        completeLocal(process, true);
        return process.toResponse();
    }

    private String messageOf(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }

    private PaymentRefundProcess findById(Long paymentRefundProcessId) {
        return paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .orElseThrow(() -> new IllegalArgumentException("환불 처리 현황을 찾을 수 없습니다."));
    }

    private PaymentRefundProcessGatewayAttempt prepareGatewayAttempt(
            PaymentRefundProcess process,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount
    ) {
        validateSameRefundProcess(process, selectedTickets, refundAmount, fullCancellation);

        if (process.getStatus() == PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
                || process.getStatus() == PaymentRefundProcessStatus.LOCAL_FAILED) {
            return new PaymentRefundProcessGatewayAttempt(process.getPaymentRefundProcessId(), false, true);
        }

        process.gatewayRetryRequested();
        process.updateRefundAccount(refundAccount);
        return new PaymentRefundProcessGatewayAttempt(process.getPaymentRefundProcessId(), true, false);
    }

    private void validateSameRefundProcess(
            PaymentRefundProcess process,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation
    ) {
        String selectedTicketIds = selectedTickets.stream()
                .map(Ticket::getTicketId)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        if (!process.getRefundAmount().equals(refundAmount)
                || process.isFullCancellation() != fullCancellation
                || !process.getSelectedTicketIds().equals(selectedTicketIds)) {
            throw new IllegalArgumentException("진행 중인 환불 처리와 요청 정보가 일치하지 않습니다.");
        }
    }

    private void completeLocal(PaymentRefundProcess process, boolean applyPaymentRefund) {
        try {
            Payment payment = process.getPayment();
            Reservation reservation = process.getReservation();
            if (applyPaymentRefund) {
                applyPaymentRefund(payment, process);
            }
            List<Ticket> selectedTickets = selectProcessTickets(process, reservation);
            boolean restoreCouponOnCancel = process.isFullCancellation() && reservation.getStatus() == ReservationStatus.PAID;

            List<Seat> cancelledSeats = cancelTickets(selectedTickets);
            applyReservationCancelStatus(reservation, process.isFullCancellation(), restoreCouponOnCancel);
            paymentRefundHistoryJpaRepository.save(
                    PaymentRefundHistory.create(payment, selectedTickets, process.getRefundAmount(), process.isFullCancellation())
            );
            process.localSucceeded();

            seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats);
            if (!cancelledSeats.isEmpty()) {
                seatCacheService.updateUserPurchaseLimit(
                        cancelledSeats.get(0).getEvent(),
                        reservation.getUserId(),
                        cancelledSeats.size(),
                        "SUB"
                );
            }
        } catch (RuntimeException e) {
            process.localFailed(messageOf(e));
            throw e;
        }
    }

    private void applyPaymentRefund(Payment payment, PaymentRefundProcess process) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (process.isFullCancellation()) {
            payment.refund();
            return;
        }
        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            payment.partialRefund(process.getRefundAmount());
        }
    }

    private List<Ticket> selectProcessTickets(PaymentRefundProcess process, Reservation reservation) {
        List<Long> ticketIds = parseTicketIds(process.getSelectedTicketIds());
        List<Ticket> tickets = ticketJpaRepository.findAllByTicketIdIn(ticketIds);
        if (tickets.size() != ticketIds.size()) {
            throw new IllegalArgumentException("환불 처리 대상 티켓을 찾을 수 없습니다.");
        }
        if (tickets.stream().anyMatch(ticket -> !ticket.getReservation().getReservationId().equals(reservation.getReservationId()))) {
            throw new IllegalArgumentException("환불 처리 대상 티켓이 예매 정보와 일치하지 않습니다.");
        }
        return tickets;
    }

    private List<Long> parseTicketIds(String selectedTicketIds) {
        if (!StringUtils.hasText(selectedTicketIds)) {
            return List.of();
        }
        return java.util.Arrays.stream(selectedTicketIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
    }

    private List<Seat> cancelTickets(List<Ticket> selectedTickets) {
        selectedTickets.forEach(ticket -> {
            if (ticket.getStatus() != TicketStatus.CANCELLED) {
                ticket.cancel();
            }
            if (ticket.getSeat() != null) {
                ticket.getSeat().available();
            }
        });

        return selectedTickets.stream()
                .map(Ticket::getSeat)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void applyReservationCancelStatus(Reservation reservation, boolean fullCancellation, boolean restoreCouponOnCancel) {
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
            if (discount.getUserCoupon() != null && discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(now);
            }
        }
    }

    private Specification<PaymentRefundProcess> makeSpec(PaymentRefundProcessCondRequest cond) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("payment", JoinType.LEFT);
                root.fetch("reservation", JoinType.LEFT);
            }

            if (cond.getPaymentRefundProcessId() != null && cond.getPaymentRefundProcessId() > 0) {
                predicates.add(cb.equal(root.get("paymentRefundProcessId"), cond.getPaymentRefundProcessId()));
            }
            if (cond.getReservationId() != null && cond.getReservationId() > 0) {
                predicates.add(cb.equal(root.get("reservation").get("reservationId"), cond.getReservationId()));
            }
            if (cond.getPaymentId() != null && cond.getPaymentId() > 0) {
                predicates.add(cb.equal(root.get("payment").get("paymentId"), cond.getPaymentId()));
            }
            if (StringUtils.hasText(cond.getPaymentNo())) {
                predicates.add(cb.like(root.get("paymentNo"), "%" + cond.getPaymentNo() + "%"));
            }
            if (cond.getMethod() != null) {
                predicates.add(cb.equal(root.get("method"), cond.getMethod()));
            }
            if (StringUtils.hasText(cond.getStatus())) {
                predicates.add(cb.equal(root.get("status"), PaymentRefundProcessStatus.valueOf(cond.getStatus())));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
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
                orders.add(new Sort.Order(Sort.Direction.fromString(parts[1]), parts[0]));
            }
        }

        return orders.isEmpty() ? Sort.by(Sort.Direction.DESC, "paymentRefundProcessId") : Sort.by(orders);
    }
}
