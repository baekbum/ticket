package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDetailResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.dto.UpdateReservationStatusRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.reservation.policy.ReservationCancellationPolicy;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistory;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistoryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.refund.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessGatewayAttempt;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundRequestContext;
import dev.bum.ticket_service.service.payment.refund.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationManagementService {

    private final Clock clock;
    private final ReservationRepository repository;
    private final SeatCacheService seatCacheService;
    private final TicketPurchaseCountService ticketPurchaseCountService;
    private final TicketJpaRepository ticketJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentRefundHistoryJpaRepository paymentRefundHistoryJpaRepository;
    private final PaymentRefundProcessService paymentRefundProcessService;
    private final CardPaymentRefundService cardPaymentRefundService;
    private final VirtualAccountPaymentRefundService virtualAccountPaymentRefundService;

    /**
     * 예매 ID로 예매 기본 정보를 조회한다.
     */
    public ReservationResponse selectById(long id) {
        return repository.selectById(id).toResponse();
    }

    /**
     * 예매 기본 정보와 티켓, 할인, 배송, 결제 요약을 함께 조회한다.
     */
    public ReservationDetailResponse selectDetailById(long id) {
        Reservation reservation = repository.selectById(id);
        List<Ticket> tickets = ticketJpaRepository.findByReservation(reservation);
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        int totalTicketAmount = tickets.stream()
                .mapToInt(ticket -> ticket.getPrice() != null ? ticket.getPrice() : 0)
                .sum();
        int totalDiscountAmount = discounts.stream()
                .mapToInt(discount -> discount.getDiscountAmount() != null ? discount.getDiscountAmount() : 0)
                .sum();

        return ReservationDetailResponse.builder()
                .reservation(reservation.toResponse())
                .tickets(tickets.stream().map(Ticket::toResponse).toList())
                .discounts(discounts.stream().map(ReservationDiscount::toResponse).toList())
                .delivery(reservationDeliveryJpaRepository.findByReservation(reservation)
                        .map(delivery -> delivery.toResponse())
                        .orElse(null))
                .payment(paymentJpaRepository.findByReservation(reservation)
                        .map(payment -> payment.toResponse())
                        .orElse(null))
                .refundHistories(paymentRefundHistoryJpaRepository.findByReservationOrderByPaymentRefundHistoryIdDesc(reservation).stream()
                        .map(PaymentRefundHistory::toResponse)
                        .toList())
                .totalTicketAmount(totalTicketAmount)
                .totalDiscountAmount(totalDiscountAmount)
                .paymentAmount(Math.max(totalTicketAmount - totalDiscountAmount, 0))
                .build();
    }

    /**
     * 관리자 검색 조건과 페이징 조건으로 예매 목록을 조회한다.
     */
    public CustomPageResponse<ReservationResponse> selectByCond(ReservationCondRequest cond) {
        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));

        Page<ReservationResponse> reservationPage = repository.selectByCond(cond, pageable).map(Reservation::toResponse);

        return CustomPageResponse.of(
                reservationPage.getContent(),
                reservationPage.getSize(),
                reservationPage.getNumber(),
                reservationPage.getTotalElements(),
                reservationPage.getTotalPages()
        );
    }

    /**
     * 관리자 기준으로 예매 티켓을 취소하고 좌석 캐시와 사용자 구매 수량을 보정한다.
     */
    @AuditLog(action = "RESERVATION_CANCEL", targetType = "RESERVATION")
    public CancelReservationResponse cancel(long id, CancelReservationRequest info) {
        Reservation reservation = repository.selectByIdForUpdate(id);
        ReservationStatus beforeStatus = reservation.getStatus();
        validateCancelableReservation(reservation);
        List<Ticket> tickets = ticketJpaRepository.findByReservation(reservation);
        List<Ticket> activeTickets = selectActiveTickets(tickets);
        List<Ticket> selectedTickets = selectTicketsForCancel(activeTickets, info.getSelectedTicketIdList());
        CancellationTicketValidator.validate(reservation, selectedTickets);
        boolean fullCancellation = isFullCancellation(activeTickets, selectedTickets);
        boolean restoreCouponOnCancel = fullCancellation
                && (reservation.getStatus() == ReservationStatus.PAID
                || reservation.getStatus() == ReservationStatus.PARTIALLY_CANCELLED);

        Long paymentRefundProcessId = null;
        try {
            CancelReservationResponse response = refundPaymentBeforeCancel(
                    reservation, info, activeTickets, selectedTickets, fullCancellation
            );
            paymentRefundProcessId = response.paymentRefundProcessId();
            if (response.previousRequestRecovered()) {
                AuditDataMapper.setFieldChange("status", beforeStatus, reservation.getStatus());
                return response;
            }
            ticketPurchaseCountService.release(reservation, selectedTickets);
            List<Seat> cancelledSeats = cancelTickets(selectedTickets);
            applyReservationCancelStatus(reservation, fullCancellation, restoreCouponOnCancel);

            seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats);
            AuditDataMapper.setFieldChange("status", beforeStatus, reservation.getStatus());
            return response;
        } catch (RuntimeException e) {
            markLocalFailed(paymentRefundProcessId, e);
            throw e;
        }
    }

    /**
     * 결제 완료 예매를 취소하는 경우, 로컬 예매 상태를 바꾸기 전에 gateway 환불을 먼저 완료한다.
     */
    private CancelReservationResponse refundPaymentBeforeCancel(
            Reservation reservation, CancelReservationRequest info, List<Ticket> activeTickets,
            List<Ticket> selectedTickets, boolean fullCancellation) {
        Payment payment = paymentJpaRepository.findByReservation(reservation)
                .orElseThrow(() -> new IllegalStateException("예매의 결제 정보를 찾을 수 없습니다."));

        if (!(payment.getMethod() == PaymentMethod.BANK_TRANSFER
                || payment.getMethod() == PaymentMethod.CREDIT_CARD)) {
            throw new IllegalArgumentException("지원하지 않는 환불 수단입니다.");
        }

        // 예매 잠금 아래 기존 미완료 요청을 찾고 이번 요청과 동일한지 먼저 확인한다.
        RefundAccountRequest account = payment.getMethod() == PaymentMethod.BANK_TRANSFER ? info.getRefundAccount() : null;
        PaymentRefundRequestContext requestContext = paymentRefundProcessService.inspectRequest(
                payment, selectedTickets, fullCancellation, account
        );

        // 무통장 입금 전은 전체 취소만 허용한다. 카드와 입금 완료 건은 아래 환불 절차로 진행한다.

        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
            boolean unpaidTickets = activeTickets.stream()
                    .allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING_PAYMENT);
            if (unpaidTickets && reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
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
                // 입금 전이므로 PG 환불 없이 상위 메서드에서 티켓·좌석·예매 취소를 진행한다.
                return CancelReservationResponse.canceledWithoutGateway(
                        reservation.getReservationId(), selectedTickets.stream().map(Ticket::getTicketId).toList()
                );
            }
        }

        if ((reservation.getStatus() != ReservationStatus.PAID
                && reservation.getStatus() != ReservationStatus.PARTIALLY_CANCELLED)
                || activeTickets.stream().anyMatch(ticket -> ticket.getStatus() != TicketStatus.PAID)) {
            throw new IllegalStateException("예매 상태와 티켓의 결제 상태가 일치하지 않습니다.");
        }
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("결제 완료 티켓과 결제 상태가 일치하지 않습니다.");
        }

        if (payment.getMethod() != PaymentMethod.CREDIT_CARD && payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("지원하지 않는 환불 수단입니다.");
        }
        // 동일 요청은 기존 상태로 재개하고, 변경 요청은 신규 검증·기존 PG 종료 확인 후 준비한다.
        PaymentRefundProcessGatewayAttempt attempt = paymentRefundProcessService.startGatewayAttempt(
                payment, selectedTickets, fullCancellation, account, requestContext
        );

        // 변경 요청이어도 기존 PG 환불이 성공했다면 기존 건만 복구하고 이번 요청은 추가 실행하지 않는다.
        if (attempt.isPreviousRequestRecoveryRequired()) {
            try {
                return paymentRefundProcessService.recoverPreviousRequest(attempt.getPaymentRefundProcessId());
            } catch (RuntimeException e) {
                markLocalFailed(attempt.getPaymentRefundProcessId(), e);
                throw e;
            }
        }

        CancellationFeeCalculator.CancellationRefundCalculation calculation = attempt.getCalculation();
        int refundAmount = calculation.refundAmount();
        int cancellationFeeAmount = calculation.cancellationFeeAmount();
        Long processId = attempt.getPaymentRefundProcessId();

        // 준비된 프로세스의 멱등 키로 PG 환불을 실행하거나, PG 재호출 없이 로컬 결제 반영만 진행한다.
        if (attempt.isGatewayRequired()) {
            boolean fullPaymentRefund = refundAmount == payment.getRefundableAmount();

            if (payment.getMethod() == PaymentMethod.CREDIT_CARD) {
                refundCardPayment(payment, refundAmount, cancellationFeeAmount, fullPaymentRefund, processId);
            } else {
                refundVirtualAccountPayment(payment, info, refundAmount, cancellationFeeAmount, fullPaymentRefund, processId);
            }

        } else if (attempt.isLocalPaymentRefundRequired()) {
            applyPaymentRefund(payment, refundAmount, cancellationFeeAmount);
        }

        savePaymentRefundHistory(processId, payment, selectedTickets, refundAmount, fullCancellation);
        restoreCouponAfterPartialCancel(reservation, fullCancellation, calculation);

        return CancelReservationResponse.canceled(
                reservation.getReservationId(), processId, selectedTickets.stream().map(Ticket::getTicketId).toList()
        );
    }

    private void refundCardPayment(
            Payment payment,
            int refundAmount,
            int cancellationFeeAmount,
            boolean fullCancellation,
            Long paymentRefundProcessId
    ) {
        try {
            if (fullCancellation) {
                cardPaymentRefundService.refundAll(payment, paymentRefundProcessId);
            } else {
                cardPaymentRefundService.refundPartial(payment, refundAmount, paymentRefundProcessId);
            }
            payment.applyCancellationFee(cancellationFeeAmount);
            markGatewaySucceeded(paymentRefundProcessId);
        } catch (RuntimeException e) {
            markGatewayFailed(paymentRefundProcessId, e);
            throw e;
        }
    }

    private void refundVirtualAccountPayment(
            Payment payment,
            CancelReservationRequest info,
            int refundAmount,
            int cancellationFeeAmount,
            boolean fullCancellation,
            Long paymentRefundProcessId
    ) {
        try {
            if (fullCancellation) {
                virtualAccountPaymentRefundService.refundAll(payment, info.getRefundAccount(), paymentRefundProcessId);
            } else {
                virtualAccountPaymentRefundService.refundPartial(payment, refundAmount, info.getRefundAccount(), paymentRefundProcessId);
            }
            payment.applyCancellationFee(cancellationFeeAmount);
            markGatewaySucceeded(paymentRefundProcessId);
        } catch (RuntimeException e) {
            markGatewayFailed(paymentRefundProcessId, e);
            throw e;
        }
    }

    private void markLocalFailed(Long paymentRefundProcessId, RuntimeException e) {
        if (paymentRefundProcessId != null) {
            paymentRefundProcessService.markLocalFailed(paymentRefundProcessId, e);
        }
    }

    private void markGatewaySucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId != null) {
            paymentRefundProcessService.markGatewaySucceeded(paymentRefundProcessId);
        }
    }

    private void markGatewayFailed(Long paymentRefundProcessId, RuntimeException e) {
        if (paymentRefundProcessId != null) {
            paymentRefundProcessService.markGatewayFailed(paymentRefundProcessId, e);
        }
    }

    private void savePaymentRefundHistory(
            Long paymentRefundProcessId,
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation
    ) {
        paymentRefundProcessService.savePaymentRefundHistory(paymentRefundProcessId, payment, selectedTickets, refundAmount, fullCancellation);
    }

    private void applyPaymentRefund(Payment payment, int refundAmount, int cancellationFeeAmount) {
        if (refundAmount == 0 && cancellationFeeAmount == 0) return;
        payment.applyCancellation(refundAmount, cancellationFeeAmount);
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

    private void restoreCouponAfterPartialCancel(
            Reservation reservation,
            boolean fullCancellation,
            CancellationFeeCalculator.CancellationRefundCalculation calculation
    ) {
        if (!fullCancellation && calculation.restoreCoupon()) {
            restoreUsedCoupons(reservation);
        }
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

    /**
     * 관리자 상태 보정 요청에 따라 예매, 티켓, 좌석, 결제, 배송, 쿠폰 상태를 함께 정정한다.
     */
    @AuditLog(action = "RESERVATION_STATUS_ADJUST", targetType = "RESERVATION")
    public ReservationDetailResponse updateStatus(long id, UpdateReservationStatusRequest request) {
        Reservation reservation = repository.selectById(id);
        ReservationStatus beforeStatus = reservation.getStatus();
        List<Ticket> tickets = ticketJpaRepository.findByReservation(reservation);
        Optional<ReservationDelivery> delivery = reservationDeliveryJpaRepository.findByReservation(reservation);
        Optional<Payment> payment = paymentJpaRepository.findByReservation(reservation);

        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("상태를 보정할 티켓 정보가 없습니다.");
        }

        ticketPurchaseCountService.prepareStatusAdjustment(reservation, tickets);
        long beforeTicketCount = TicketPurchaseCountService.activeCount(tickets);
        ReservationStatus targetStatus = request.getStatus();
        List<Seat> lockedSeats = new ArrayList<>();
        List<Seat> reservedSeats = new ArrayList<>();
        List<Seat> availableSeats = new ArrayList<>();

        switch (targetStatus) {
            case PENDING_PAYMENT -> {
                reservation.pendingPayment();
                tickets.forEach(ticket -> {
                    ticket.pendingPayment();
                    ticket.getSeat().lock();
                    lockedSeats.add(ticket.getSeat());
                });
                payment.ifPresent(Payment::ready);
                delivery.ifPresent(ReservationDelivery::ready);
            }
            case PAID -> {
                reservation.paid();
                tickets.forEach(ticket -> {
                    ticket.paid();
                    ticket.getSeat().reserved();
                    reservedSeats.add(ticket.getSeat());
                });
                payment.ifPresent(foundPayment -> foundPayment.complete(null));
                delivery.ifPresent(ReservationDelivery::ready);
            }
            case PARTIALLY_CANCELLED -> {
                List<Ticket> selectedTickets = selectTicketsForPartialCancel(tickets, request.getSelectedTicketIdList());
                if (selectedTickets.size() == tickets.size()) {
                    throw new IllegalArgumentException("전체 티켓 취소는 CANCELLED 상태로 보정해주세요.");
                }

                selectedTickets.forEach(ticket -> {
                    ticket.cancel();
                    ticket.getSeat().available();
                    availableSeats.add(ticket.getSeat());
                });

                tickets.stream()
                        .filter(ticket -> !selectedTickets.contains(ticket))
                        .forEach(ticket -> {
                            ticket.paid();
                            ticket.getSeat().reserved();
                            reservedSeats.add(ticket.getSeat());
                        });

                reservation.partial_cancel();
                payment.ifPresent(foundPayment -> foundPayment.complete(null));
            }
            case CANCELLED -> {
                reservation.cancel();
                tickets.forEach(ticket -> {
                    ticket.cancel();
                    ticket.getSeat().available();
                    availableSeats.add(ticket.getSeat());
                });
                delivery.ifPresent(ReservationDelivery::cancel);
                payment.ifPresent(foundPayment -> {
                    if (foundPayment.getStatus() == PaymentStatus.PAID) {
                        foundPayment.refund();
                    } else {
                        foundPayment.cancel();
                    }
                });
                restoreUsedCoupons(reservation);
            }
            case EXPIRED -> {
                reservation.expire();
                tickets.forEach(ticket -> {
                    ticket.expire();
                    ticket.getSeat().available();
                    availableSeats.add(ticket.getSeat());
                });
                delivery.ifPresent(ReservationDelivery::cancel);
                payment.ifPresent(Payment::expire);
                restoreUsedCoupons(reservation);
            }
        }

        ticketPurchaseCountService.applyStatusAdjustment(reservation, beforeTicketCount, tickets);
        AuditDataMapper.setFieldChange("status", beforeStatus, targetStatus);
        syncAdjustedSeatCache(lockedSeats, reservedSeats, availableSeats);

        return selectDetailById(id);
    }

    /**
     * 부분 취소 대상으로 선택된 티켓이 현재 예매에 속하는지 검증하고 반환한다.
     */
    private List<Ticket> selectTicketsForPartialCancel(List<Ticket> tickets, List<Long> selectedTicketIdList) {
        if (selectedTicketIdList == null || selectedTicketIdList.isEmpty()) {
            throw new IllegalArgumentException("부분 취소는 취소할 티켓을 선택해야 합니다.");
        }

        List<Ticket> selectedTickets = tickets.stream()
                .filter(ticket -> selectedTicketIdList.contains(ticket.getTicketId()))
                .toList();

        if (selectedTickets.size() != selectedTicketIdList.size()) {
            throw new IllegalArgumentException("선택한 티켓 중 해당 예매에 속하지 않는 티켓이 있습니다.");
        }

        return selectedTickets;
    }

    /**
     * 상태 보정으로 변경된 좌석 목록을 Redis 캐시에 반영한다.
     */
    private void syncAdjustedSeatCache(List<Seat> lockedSeats, List<Seat> reservedSeats, List<Seat> availableSeats) {
        if (!lockedSeats.isEmpty()) {
            seatCacheService.syncLockedSeatsAfterCommit(lockedSeats);
        }
        if (!reservedSeats.isEmpty()) {
            seatCacheService.syncReservedSeatsAfterCommit(reservedSeats);
        }
        if (!availableSeats.isEmpty()) {
            seatCacheService.syncAvailableSeatsAfterCommit(availableSeats);
        }
    }

    /**
     * 전체 취소 또는 만료 처리 시 예매 할인 스냅샷에 연결된 사용 쿠폰을 복구한다.
     */
    private void restoreUsedCoupons(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now();
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        for (ReservationDiscount discount : discounts) {
            if (discount.getUserCoupon() != null && discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(now);
            }
        }
    }

    /**
     * 요청 sort 문자열을 Spring Data Sort 객체로 변환한다.
     */
    private Sort makeSortInfo(List<String> sorts) {
        Sort sort = Sort.unsorted();
        if (sorts != null && !sorts.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();

            for (String infoStr : sorts) {
                String[] infos = infoStr.split("-");

                if (infos.length == 2) {
                    String field = infos[0];
                    String direction = infos[1];
                    orders.add(new Sort.Order(Sort.Direction.fromString(direction), field));
                }
            }
            sort = Sort.by(orders);
        }

        return sort;
    }

}
