package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse.Outcome;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessCondRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationResponse;
import dev.bum.common.service.ticket.reservation.policy.ReservationCancellationPolicy;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayRefundClient;
import dev.bum.ticket_service.exception.reservation.ReservationCancellationInProgressException;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistory;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistoryJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundSnapshot;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator.CancellationRefundCalculation;
import dev.bum.ticket_service.service.reservation.reservation.CancellationTicketValidator;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundProcessService {

    private final Clock clock;
    private final PaymentRefundProcessCreator paymentRefundProcessCreator;
    private final PaymentRefundRollbackRecorder paymentRefundRollbackRecorder;
    private final PaymentGatewayRefundClient paymentGatewayRefundClient;
    private final PaymentRefundProcessJpaRepository paymentRefundProcessJpaRepository;
    private final PaymentRefundHistoryJpaRepository paymentRefundHistoryJpaRepository;
    private final ReservationJpaRepository reservationJpaRepository;
    private final TicketJpaRepository ticketJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final UserCouponRepository userCouponRepository;
    private final SeatCacheService seatCacheService;
    private final TicketPurchaseCountService ticketPurchaseCountService;

    @Transactional(readOnly = true)
    public CustomPageResponse<PaymentRefundProcessResponse> selectByCond(
            PaymentRefundProcessCondRequest cond) {
        PaymentRefundProcessCondRequest normalizedCond =
                cond != null ? cond : PaymentRefundProcessCondRequest.builder().build();
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

    @Transactional
    public PaymentRefundRequestContext inspectRequest(
            Payment payment, List<Ticket> selectedTickets, boolean fullCancellation, RefundAccountRequest refundAccount) {

        // A가 Reservation을 잠근 상태에서 기존 프로세스를 일반 조회한다.
        PaymentRefundProcess previous = paymentRefundProcessJpaRepository
                .findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(
                        payment.getReservation(), List.of(
                                PaymentRefundProcessStatus.REQUESTED, PaymentRefundProcessStatus.GATEWAY_FAILED,
                                PaymentRefundProcessStatus.GATEWAY_SUCCEEDED, PaymentRefundProcessStatus.LOCAL_FAILED
                        )
                ).orElse(null);

        if (previous == null) {
            return PaymentRefundRequestContext.builder()
                    .previousProcessId(null)
                    .sameRequest(false)
                    .build();
        }

        trackRollback(previous);

        // 금액을 다시 계산하지 않고 대상 티켓·전체 취소 여부·환불 계좌로 요청 동일성을 판단한다.
        return PaymentRefundRequestContext.builder()
                .previousProcessId(previous.getPaymentRefundProcessId())
                .sameRequest(sameRequest(previous, selectedTickets, fullCancellation, refundAccount))
                .build();
    }

    @Transactional
    public PaymentRefundProcessGatewayAttempt startGatewayAttempt(
            Payment payment, List<Ticket> selectedTickets, boolean fullCancellation,
            RefundAccountRequest refundAccount, PaymentRefundRequestContext requestContext) {

        LocalDateTime requestedAt = LocalDateTime.now(clock);

        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER
                && (refundAccount == null || refundAccount.getBankCompany() == null
                || !StringUtils.hasText(refundAccount.getAccountNumber())
                || !StringUtils.hasText(refundAccount.getAccountHolder()))) {
            throw new IllegalArgumentException("환불 계좌 정보가 필요합니다.");
        }

        // 기존 요청이 없으면 B에서 최초 기록을 커밋하고 A에서 일반 조회한다.
        if (!requestContext.hasPreviousRequest()) {
            return createNewAttempt(payment, selectedTickets, fullCancellation, refundAccount, requestedAt);
        }

        // A의 예매 잠금이 유지되므로 기존 프로세스에 별도 잠금은 필요하지 않다.
        PaymentRefundProcess previous = paymentRefundProcessJpaRepository
                .findById(requestContext.previousProcessId())
                .orElseThrow(() -> new IllegalStateException("기존 환불 요청을 찾을 수 없습니다."));

        if (!Objects.equals(previous.getReservation().getReservationId(), payment.getReservation().getReservationId())
                || previous.getStatus() == PaymentRefundProcessStatus.LOCAL_SUCCEEDED
                || previous.getStatus() == PaymentRefundProcessStatus.SUPERSEDED) {
            throw new IllegalStateException("기존 환불 요청 상태가 변경되었습니다. 다시 시도해 주세요.");
        }

        // 4. 동일 요청은 최초 스냅샷을 유지하고 기존 상태에 따라 재개한다.
        if (requestContext.sameRequest()) {
            return resumeSameRequest(previous, payment, selectedTickets, fullCancellation, refundAccount);
        }
        // 5. 변경 요청은 신규 접수 가능 여부와 기존 PG 결과를 확인한 뒤 처리한다.
        return prepareChangedRequest(previous, payment, selectedTickets, fullCancellation, refundAccount, requestedAt);
    }

    private PaymentRefundProcessGatewayAttempt resumeSameRequest(
            PaymentRefundProcess previous, Payment payment, List<Ticket> selectedTickets,
            boolean fullCancellation, RefundAccountRequest refundAccount) {

        if (hasGatewaySucceeded(previous)) {
            return attempt(previous, RefundAction.APPLY_LOCAL);
        }

        Outcome outcome = resolvePreviousRequest(previous, payment, false);

        if (outcome == Outcome.SUCCEEDED) {
            return attempt(previous, RefundAction.APPLY_LOCAL);
        }

        if (outcome == Outcome.CLOSED) {
            // PG만 종료되고 ticket 저장이 실패했던 경우, 최초 금액·접수 시각을 새 키로 이어간다.
            previous.superseded();
            PaymentRefundSnapshot snapshot = previous.getRefund().getSnapshot();
            return createAttempt(
                    payment, selectedTickets, fullCancellation, refundAccount,
                    calculation(snapshot), snapshot.getCapturedAt()
            );
        }

        // PG 미실행·실패 요청은 같은 멱등 키로 재시도한다. 마감과 수수료는 다시 계산하지 않는다.
        previous.gatewayRetryRequested();

        return attempt(previous, RefundAction.REQUEST_GATEWAY);
    }

    private PaymentRefundProcessGatewayAttempt prepareChangedRequest(
            PaymentRefundProcess previous, Payment payment, List<Ticket> selectedTickets,
            boolean fullCancellation, RefundAccountRequest refundAccount, LocalDateTime requestedAt) {

        // 이미 PG 성공이 확인된 건은 새 환불 대신 기존 스냅샷으로 로컬 반영을 복구한다.
        if (hasGatewaySucceeded(previous)) {
            return attempt(previous, RefundAction.RECOVER_PREVIOUS_REQUEST);
        }

        // 신규 마감에 걸리면 기존 PG 요청을 종료하지 않고 그대로 유지한다.
        validateNewCancellation(payment.getReservation(), requestedAt);
        Outcome outcome = resolvePreviousRequest(previous, payment, true);

        if (outcome == Outcome.SUCCEEDED) {
            return attempt(previous, RefundAction.RECOVER_PREVIOUS_REQUEST);
        }

        if (outcome != Outcome.CLOSED) {
            throw new IllegalStateException("이전 환불 요청의 종료를 확인할 수 없습니다.");
        }

        // PG에서 기존 ID 실행 차단을 확정한 뒤 기존 건을 종료하고 새 요청을 만든다. 이력은 삭제하지 않는다.
        previous.superseded();

        return createNewAttempt(payment, selectedTickets, fullCancellation, refundAccount, requestedAt);
    }

    private boolean hasGatewaySucceeded(PaymentRefundProcess process) {
        return process.getStatus() == PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
                || process.getStatus() == PaymentRefundProcessStatus.LOCAL_FAILED;
    }

    private Outcome resolvePreviousRequest(PaymentRefundProcess previous, Payment payment, boolean closeIfUnexecuted) {
        GatewayRefundResolutionResponse result = paymentGatewayRefundClient.resolve(
                GatewayRefundResolutionRequest.builder()
                        .paymentRefundProcessId(previous.getPaymentRefundProcessId())
                        .paymentNo(payment.getPaymentNo())
                        .method(payment.getMethod())
                        .refundAmount(BigDecimal.valueOf(previous.getRefundAmount()))
                        .closeIfUnexecuted(closeIfUnexecuted)
                        .build()
        );
        if (result == null || result.outcome() == null
                || !previous.getPaymentRefundProcessId().equals(result.paymentRefundProcessId())
                || !payment.getPaymentNo().equals(result.paymentNo())) {
            throw new IllegalStateException("PG 환불 처리 결과를 확인할 수 없습니다.");
        }
        if (result.outcome() == Outcome.SUCCEEDED) {
            // PG 결과는 A에서 반영한다. A가 롤백되면 잠금 해제 후 별도 기록 트랜잭션에 남긴다.
            previous.gatewaySucceeded();
        }
        return result.outcome();
    }

    private PaymentRefundProcessGatewayAttempt createNewAttempt(
            Payment payment, List<Ticket> selectedTickets, boolean full, RefundAccountRequest account, LocalDateTime requestedAt) {

        Long processId = paymentRefundProcessCreator.createNewRequest(payment, selectedTickets, full, account, requestedAt);

        return loadCreatedAttempt(processId);
    }

    private PaymentRefundProcessGatewayAttempt createAttempt(
            Payment payment, List<Ticket> selectedTickets, boolean full, RefundAccountRequest account,
            CancellationRefundCalculation calculated, LocalDateTime capturedAt) {

        Long processId = paymentRefundProcessCreator.copyOriginalRequest(
                payment, selectedTickets, full, account, calculated, capturedAt
        );

        return loadCreatedAttempt(processId);
    }

    private PaymentRefundProcessGatewayAttempt loadCreatedAttempt(Long processId) {
        // B의 최초 기록은 A가 롤백돼도 남는다. 예매 잠금 아래 일반 조회한다.
        PaymentRefundProcess process = paymentRefundProcessJpaRepository.findById(processId)
                .orElseThrow(() -> new IllegalStateException("생성한 환불 요청을 찾을 수 없습니다."));

        trackRollback(process);

        return attempt(process, process.getRefundAmount() > 0 ? RefundAction.REQUEST_GATEWAY : RefundAction.APPLY_LOCAL);
    }

    private void validateNewCancellation(Reservation reservation, LocalDateTime requestedAt) {
        ReservationCancellationPolicy.validateNewRequest(requestedAt,
                reservation.getEvent() == null ? null : reservation.getEvent().getCancelDeadlineAt());
    }

    private boolean sameRequest(PaymentRefundProcess process, List<Ticket> tickets, boolean full, RefundAccountRequest account) {
        Set<Long> original = process.getRefund().getSnapshot().getTickets().stream()
                .map(ticket -> ticket.getTicketId()).collect(Collectors.toSet());
        Set<Long> selected = tickets.stream().map(Ticket::getTicketId).collect(Collectors.toSet());

        if (!original.equals(selected) || process.isFullCancellation() != full) return false;

        try {
            process.validateRefundAccount(account);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private PaymentRefundProcessGatewayAttempt attempt(PaymentRefundProcess process, RefundAction action) {
        return PaymentRefundProcessGatewayAttempt.builder()
                .paymentRefundProcessId(process.getPaymentRefundProcessId())
                .action(action)
                .calculation(calculation(process.getRefund().getSnapshot()))
                .build();
    }

    private CancellationRefundCalculation calculation(PaymentRefundSnapshot snapshot) {
        return CancellationRefundCalculation.builder()
                .refundAmount(snapshot.getRefundAmount())
                .cancellationFeeAmount(snapshot.getCancellationFeeAmount())
                .deliveryRefundAmount(snapshot.getDeliveryRefundAmount())
                .discountDeductionAmount(snapshot.getDiscountAmount())
                .restoreCoupon(snapshot.isRestoreCoupon())
                .build();
    }

    @Transactional
    public void markGatewaySucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::gatewaySucceeded);
    }

    @Transactional
    public void markGatewayFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .ifPresent(process -> process.gatewayFailed(messageOf(throwable)));
    }

    @Transactional
    public void markLocalFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .ifPresent(process -> process.localFailed(messageOf(throwable)));
    }

    /** 티켓·좌석 취소 후 호출하며, 성공 이력과 완료 상태를 같은 A 트랜잭션에 반영한다. */
    @Transactional
    public void completeCancellation(
            Long paymentRefundProcessId,
            Payment payment) {

        if (paymentRefundProcessId == null) {
            return;
        }

        PaymentRefundProcess process = findById(paymentRefundProcessId);
        savePaymentRefundHistory(process, payment);
        process.localSucceeded();
    }

    @Transactional
    public CancelReservationResponse recoverPreviousRequest(Long paymentRefundProcessId) {
        PaymentRefundProcessResponse completed = completeLocal(paymentRefundProcessId);

        return CancelReservationResponse.recovered(
                completed.getReservationId(), paymentRefundProcessId, parseTicketIds(completed.getSelectedTicketIds())
        );
    }

    @AuditLog(
            action = "PAYMENT_REFUND_PROCESS_LOCAL_COMPLETE",
            targetType = "PAYMENT_REFUND_PROCESS")
    @Transactional
    public PaymentRefundProcessResponse completeLocal(Long paymentRefundProcessId) {
        Long reservationId = paymentRefundProcessJpaRepository.findReservationIdById(paymentRefundProcessId)
                .orElseThrow(() -> new IllegalArgumentException("환불 처리 현황을 찾을 수 없습니다."));

        // 프로세스나 티켓을 읽기 전에 사용자·관리자 취소와 같은 예매 행을 잠근다.
        try {
            reservationJpaRepository.findByIdForUpdateNowait(reservationId)
                    .orElseThrow(() -> new IllegalArgumentException("예매 정보를 찾을 수 없습니다."));
        } catch (PessimisticLockingFailureException e) {
            throw new ReservationCancellationInProgressException(e);
        }

        PaymentRefundProcess process = paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .orElseThrow(() -> new IllegalArgumentException("환불 처리 현황을 찾을 수 없습니다."));

        trackRollback(process);

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

    private void trackRollback(PaymentRefundProcess process) {
        // 트랜잭션 동기화가 활성화된 경우에만 롤백 후 기록을 예약한다.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;

        // 같은 트랜잭션에서 동일 프로세스의 후처리를 중복 등록하지 않는다.
        boolean registered = TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(sync -> sync instanceof RefundRollbackSynchronization existing
                        && Objects.equals(existing.process.getPaymentRefundProcessId(), process.getPaymentRefundProcessId()));

        if (!registered) {
            TransactionSynchronizationManager.registerSynchronization(new RefundRollbackSynchronization(process));
        }
    }

    private final class RefundRollbackSynchronization implements TransactionSynchronization {
        private final PaymentRefundProcess process;
        private final Long reservationId;
        private final LocalDateTime expectedUpdatedAt;

        private RefundRollbackSynchronization(PaymentRefundProcess process) {
            this.process = process;
            reservationId = process.getReservation().getReservationId();
            expectedUpdatedAt = process.getUpdatedAt();
        }

        @Override
        public void afterCompletion(int status) {
            if (status != STATUS_ROLLED_BACK || process.getStatus() == PaymentRefundProcessStatus.REQUESTED) return;
            // 이 시점에는 A의 DB 롤백과 잠금 해제가 끝났다. A 실행 중에는 별도 갱신 트랜잭션을 만들지 않는다.
            PaymentRefundRollbackRecord record = PaymentRefundRollbackRecord.builder()
                    .processId(process.getPaymentRefundProcessId())
                    .reservationId(reservationId)
                    .expectedUpdatedAt(expectedUpdatedAt)
                    .observedStatus(process.getStatus())
                    .failureReason(process.getFailureReason() == null
                            ? "예매 취소 트랜잭션이 롤백되었습니다." : process.getFailureReason())
                    .build();
            try {
                paymentRefundRollbackRecorder.record(record);
            } catch (RuntimeException e) {
                // 기록까지 실패해도 B의 스냅샷과 PG 멱등 키로 다음 요청에서 결과를 조회해 복구할 수 있다.
                log.error("환불 롤백 결과 기록 실패: processId={}", record.processId(), e);
            }
        }
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
                    && (reservation.getStatus() == ReservationStatus.PAID
                    || reservation.getStatus() == ReservationStatus.PARTIALLY_CANCELLED);

            ticketPurchaseCountService.release(reservation, selectedTickets);
            List<Seat> cancelledSeats = cancelTickets(selectedTickets);
            applyReservationCancelStatus(
                    reservation, process.isFullCancellation(), restoreCouponOnCancel);
            if (!process.isFullCancellation() && process.getRefund().getSnapshot().isRestoreCoupon()) {
                restoreUsedCoupons(reservation);
            }

            savePaymentRefundHistory(process, payment);

            process.localSucceeded();

            seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats, reservation.getUserId(), reservation.getOrderId());
        } catch (RuntimeException e) {
            process.localFailed(messageOf(e));
            throw e;
        }
    }

    private void applyPaymentRefund(Payment payment, PaymentRefundProcess process) {
        if (process.getRefundAmount() == 0 && process.getCancellationFeeAmount() == 0) return;
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
            Payment payment) {
        if (paymentRefundHistoryJpaRepository.existsByPaymentRefundProcess(process)) {
            throw new IllegalStateException("이미 환불 이력이 저장된 환불 처리입니다.");
        }

        paymentRefundHistoryJpaRepository.save(PaymentRefundHistory.create(process, payment));
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
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        for (ReservationDiscount discount : discounts) {
            if (discount.getUserCoupon() == null) continue;

            userCouponRepository.lockForUpdate(discount.getUserCoupon());
            if (discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(LocalDateTime.now());
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
