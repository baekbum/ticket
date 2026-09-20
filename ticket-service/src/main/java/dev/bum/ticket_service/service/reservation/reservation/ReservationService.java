package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationDetailResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.PaymentRefundProcessGatewayAttempt;
import dev.bum.ticket_service.service.payment.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository repository;
    private final ReservationManagementService reservationManagementService;
    private final SeatCacheService seatCacheService;
    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentRefundProcessService paymentRefundProcessService;
    private final CardPaymentRefundService cardPaymentRefundService;
    private final VirtualAccountPaymentRefundService virtualAccountPaymentRefundService;
    private final TicketJpaRepository ticketJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;

    /**
     * 로그인 사용자가 본인 예매 기본 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public ReservationResponse selectMyReservation(String currentUserId, long id) {
        Reservation reservation = repository.selectById(id);
        validateOwner(currentUserId, reservation);
        return reservation.toResponse();
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponse selectMyReservationDetail(String currentUserId, long id) {
        validateOwner(currentUserId, repository.selectById(id));
        return reservationManagementService.selectDetailById(id);
    }

    /**
     * 로그인 사용자 ID를 검색 조건에 주입해 본인 예매 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<ReservationResponse> selectMyReservations(String currentUserId, ReservationCondRequest cond) {
        cond.setUserId(currentUserId);
        return selectByCond(cond);
    }

    /**
     * 로그인 사용자가 본인 예매를 취소한다.
     */
    @AuditLog(action = "RESERVATION_CANCEL", targetType = "RESERVATION")
    public void cancelMyReservation(String currentUserId, long id, CancelReservationRequest info) {
        Reservation reservation = repository.selectById(id);
        Object beforeStatus = reservation.getStatus();
        validateOwner(currentUserId, reservation);
        info.setUserId(currentUserId);
        cancel(id, reservation, info);
        AuditDataMapper.setFieldChange("status", beforeStatus, "CANCELLED");
    }

    private CustomPageResponse<ReservationResponse> selectByCond(ReservationCondRequest cond) {
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

    private void cancel(long id, Reservation reservation, CancelReservationRequest info) {
        validateCancelableReservation(reservation);
        List<Ticket> tickets = ticketJpaRepository.findByReservation(reservation);
        List<Ticket> activeTickets = selectActiveTickets(tickets);
        List<Ticket> selectedTickets = selectTicketsForCancel(activeTickets, info.getSelectedTicketIdList());
        boolean fullCancellation = isFullCancellation(activeTickets, selectedTickets);
        boolean restoreCouponOnCancel = fullCancellation
                && (reservation.getStatus() == ReservationStatus.PAID
                || reservation.getStatus() == ReservationStatus.PARTIALLY_CANCELLED);

        Long paymentRefundProcessId = null;
        try {
            paymentRefundProcessId = refundPaymentBeforeCancel(reservation, info, activeTickets, selectedTickets, fullCancellation);
            List<Seat> cancelledSeats = cancelTickets(selectedTickets);
            applyReservationCancelStatus(reservation, fullCancellation, restoreCouponOnCancel);
            registerRefundProcessCompletion(paymentRefundProcessId);

            seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats);
            if (!cancelledSeats.isEmpty()) {
                seatCacheService.updateUserPurchaseLimit(
                        cancelledSeats.get(0).getEvent(),
                        info.getUserId(),
                        cancelledSeats.size(),
                        "SUB"
                );
            }
        } catch (RuntimeException e) {
            markLocalFailed(paymentRefundProcessId, e);
            throw e;
        }
    }

    /**
     * 결제 완료 예매를 취소하는 경우, 로컬 예매 상태를 바꾸기 전에 gateway 환불을 먼저 완료한다.
     */
    private Long refundPaymentBeforeCancel(
            Reservation reservation,
            CancelReservationRequest info,
            List<Ticket> activeTickets,
            List<Ticket> selectedTickets,
            boolean fullCancellation
    ) {
        if (reservation.getStatus() != ReservationStatus.PAID
                && reservation.getStatus() != ReservationStatus.PARTIALLY_CANCELLED) {
            return null;
        }

        return paymentJpaRepository.findByReservation(reservation)
                .map(payment -> {
                    if (payment.getStatus() != PaymentStatus.PAID
                            && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                        return null;
                    }

                    if (payment.getMethod() == PaymentMethod.CREDIT_CARD) {
                        CancellationFeeCalculator.CancellationRefundCalculation calculation =
                                calculateRefund(reservation, payment, activeTickets, selectedTickets, fullCancellation);
                        int refundAmount = calculation.refundAmount();
                        int cancellationFeeAmount = calculation.cancellationFeeAmount();
                        if (refundAmount == 0) {
                            payment.applyCancellation(0, cancellationFeeAmount);
                            restoreCouponAfterPartialCancel(reservation, fullCancellation, calculation);
                            return null;
                        }
                        boolean fullPaymentRefund = refundAmount == payment.getRefundableAmount();

                        PaymentRefundProcessGatewayAttempt gatewayAttempt = paymentRefundProcessService.startGatewayAttempt(
                                payment, selectedTickets, refundAmount, cancellationFeeAmount, fullCancellation, null);
                        Long paymentRefundProcessId = gatewayAttempt.getPaymentRefundProcessId();

                        if (gatewayAttempt.isGatewayRequired()) {
                            refundCardPayment(payment, refundAmount, cancellationFeeAmount, fullPaymentRefund, paymentRefundProcessId);
                        } else if (gatewayAttempt.isLocalPaymentRefundRequired()) {
                            applyPaymentRefund(payment, refundAmount, cancellationFeeAmount);
                        }

                        savePaymentRefundHistory(paymentRefundProcessId, payment, selectedTickets, refundAmount, fullCancellation);
                        restoreCouponAfterPartialCancel(reservation, fullCancellation, calculation);
                        return paymentRefundProcessId;
                    }

                    if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
                        CancellationFeeCalculator.CancellationRefundCalculation calculation =
                                calculateRefund(reservation, payment, activeTickets, selectedTickets, fullCancellation);
                        int refundAmount = calculation.refundAmount();
                        int cancellationFeeAmount = calculation.cancellationFeeAmount();
                        if (refundAmount == 0) {
                            payment.applyCancellation(0, cancellationFeeAmount);
                            restoreCouponAfterPartialCancel(reservation, fullCancellation, calculation);
                            return null;
                        }
                        boolean fullPaymentRefund = refundAmount == payment.getRefundableAmount();

                        PaymentRefundProcessGatewayAttempt gatewayAttempt = paymentRefundProcessService.startGatewayAttempt(
                                payment, selectedTickets, refundAmount, cancellationFeeAmount, fullCancellation, info.getRefundAccount());
                        Long paymentRefundProcessId = gatewayAttempt.getPaymentRefundProcessId();

                        if (gatewayAttempt.isGatewayRequired()) {
                            refundVirtualAccountPayment(payment, info, refundAmount, cancellationFeeAmount, fullPaymentRefund, paymentRefundProcessId);
                        } else if (gatewayAttempt.isLocalPaymentRefundRequired()) {
                            applyPaymentRefund(payment, refundAmount, cancellationFeeAmount);
                        }

                        savePaymentRefundHistory(paymentRefundProcessId, payment, selectedTickets, refundAmount, fullCancellation);
                        restoreCouponAfterPartialCancel(reservation, fullCancellation, calculation);
                        return paymentRefundProcessId;
                    }

                    return null;
        }).orElse(null);
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
                cardPaymentRefundService.refundAll(payment);
            } else {
                cardPaymentRefundService.refundPartial(payment, refundAmount);
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
                virtualAccountPaymentRefundService.refundAll(payment, info.getRefundAccount());
            } else {
                virtualAccountPaymentRefundService.refundPartial(payment, refundAmount, info.getRefundAccount());
            }
            payment.applyCancellationFee(cancellationFeeAmount);

            markGatewaySucceeded(paymentRefundProcessId);
        } catch (RuntimeException e) {
            markGatewayFailed(paymentRefundProcessId, e);
            throw e;
        }
    }

    private void registerRefundProcessCompletion(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            paymentRefundProcessService.markLocalSucceeded(paymentRefundProcessId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paymentRefundProcessService.markLocalSucceeded(paymentRefundProcessId);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    paymentRefundProcessService.markLocalFailed(paymentRefundProcessId, null);
                }
            }
        });
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
        payment.applyCancellation(refundAmount, cancellationFeeAmount);
    }

    private void validateCancelableReservation(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new IllegalArgumentException("이미 취소되었거나 만료된 예매입니다.");
        }
        if (reservation.getEvent() != null
                && reservation.getEvent().getCancelDeadlineAt() != null
                && LocalDateTime.now().isAfter(reservation.getEvent().getCancelDeadlineAt())) {
            throw new IllegalArgumentException("취소 가능 기한이 지났습니다.");
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

    private CancellationFeeCalculator.CancellationRefundCalculation calculateRefund(
            Reservation reservation,
            Payment payment,
            List<Ticket> activeTickets,
            List<Ticket> selectedTickets,
            boolean fullCancellation
    ) {
        return CancellationFeeCalculator.calculate(
                reservation,
                payment,
                activeTickets,
                selectedTickets,
                reservationDiscountJpaRepository.findByReservation(reservation),
                fullCancellation,
                LocalDateTime.now());
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

    private void restoreUsedCoupons(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now();
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        for (ReservationDiscount discount : discounts) {
            if (discount.getUserCoupon() != null && discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(now);
            }
        }
    }

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

    private void validateOwner(String currentUserId, Reservation reservation) {
        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("본인 예약만 조회하거나 취소할 수 있습니다.");
        }
    }
}
