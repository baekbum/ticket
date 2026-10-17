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
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.reservation.policy.ReservationCancellationPolicy;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private final Clock clock;
    private final ReservationRepository repository;
    private final ReservationManagementService reservationManagementService;
    private final SeatCacheService seatCacheService;
    private final TicketPurchaseCountService ticketPurchaseCountService;
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
    public CancelReservationResponse cancelMyReservation(String currentUserId, long id, CancelReservationRequest info) {
        Reservation reservation = repository.selectByIdForUpdate(id);
        Object beforeStatus = reservation.getStatus();
        validateOwner(currentUserId, reservation);
        info.setUserId(currentUserId);
        CancelReservationResponse response = cancel(reservation, info);
        AuditDataMapper.setFieldChange("status", beforeStatus, reservation.getStatus());
        return response;
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

    private CancelReservationResponse cancel(Reservation reservation, CancelReservationRequest info) {
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

        // paymentRefundProcessId가 null인 경우는 pg서비스를 호출하지 않고 취소 과정을 진행한다.
        // 예를 들어, 무통장 입금인 경우, 입금 전에 예매를 취소하는 케이스이다.
        try {
            CancelReservationResponse response = refundPaymentBeforeCancel(
                    reservation, info, activeTickets, selectedTickets, fullCancellation
            );

            paymentRefundProcessId = response.paymentRefundProcessId();

            if (response.previousRequestRecovered()) {
                return response;
            }

            ticketPurchaseCountService.release(reservation, selectedTickets);
            List<Seat> cancelledSeats = cancelTickets(selectedTickets);
            applyReservationCancelStatus(reservation, fullCancellation, restoreCouponOnCancel);

            seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats);

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
        // 기존 요청이 있는지, 기존 요청과 새로운 요청이 동일한지 확인
        PaymentRefundRequestContext requestContext = paymentRefundProcessService.inspectRequest(
                payment, selectedTickets, fullCancellation, account
        );

        // 무통장 입금 전은 전체 취소만 허용한다. 카드와 입금 완료 건은 아래 환불 절차로 진행한다.
        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
            boolean unpaidTickets = activeTickets.stream()
                    .allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING_PAYMENT);

            // 입금 된 적이 없는 케이스.
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

                // 신규 예매 취소를 할 수 있는지 시간 검증.
                ReservationCancellationPolicy.validateNewRequest(
                        LocalDateTime.now(clock),
                        reservation.getEvent() == null ? null : reservation.getEvent().getCancelDeadlineAt()
                );

                // 입금 된 적이 없으므로, pg 서비스에 요청을 보낼 필요 없이, 티켓 서비스 내부에서 예매 취소 상태로 변경한다.
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
