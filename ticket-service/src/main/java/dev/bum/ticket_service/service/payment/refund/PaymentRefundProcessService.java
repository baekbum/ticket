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

    // 처리 시각을 제공하는 시계.
    private final Clock clock;
    // 결제 환불 처리 데이터 생성을 담당하는 도구.
    private final PaymentRefundProcessCreator paymentRefundProcessCreator;
    // 결제 환불 결과와 실패 이력을 기록하는 도구.
    private final PaymentRefundRollbackRecorder paymentRefundRollbackRecorder;
    // 게이트웨이 환불 API를 호출하는 클라이언트.
    private final PaymentGatewayRefundClient paymentGatewayRefundClient;
    // 결제 환불 처리 데이터를 조회하고 저장하는 레포지토리.
    private final PaymentRefundProcessJpaRepository paymentRefundProcessJpaRepository;
    // 결제 환불 이력 데이터를 조회하고 저장하는 레포지토리.
    private final PaymentRefundHistoryJpaRepository paymentRefundHistoryJpaRepository;
    // 예매 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationJpaRepository reservationJpaRepository;
    // 티켓 데이터를 조회하고 저장하는 레포지토리.
    private final TicketJpaRepository ticketJpaRepository;
    // 예매 할인 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    // 사용자 쿠폰 데이터를 조회하고 저장하는 레포지토리.
    private final UserCouponRepository userCouponRepository;
    // 좌석 캐시 업무 처리를 담당하는 서비스.
    private final SeatCacheService seatCacheService;
    // 티켓 구매 수량 업무 처리를 담당하는 서비스.
    private final TicketPurchaseCountService ticketPurchaseCountService;

    /**
     * 검색 조건에 맞는 결제 환불 처리 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<PaymentRefundProcessResponse> selectByCond(
            PaymentRefundProcessCondRequest cond
    ) {

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

    /**
     * 기존 요청과 현재 요청의 처리 상태를 점검한다.
     */
    @Transactional
    public PaymentRefundRequestContext inspectRequest(
            Payment payment,
            List<Ticket> selectedTickets,
            boolean fullCancellation,
            RefundAccountRequest refundAccount
    ) {

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

    /**
     * 게이트웨이 환불 성공 상태를 기록한다.
     */
    @Transactional
    public void markGatewaySucceeded(Long paymentRefundProcessId) {

        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::gatewaySucceeded);
    }

    /**
     * 게이트웨이 환불 실패 상태를 기록한다.
     */
    @Transactional
    public void markGatewayFailed(Long paymentRefundProcessId, Throwable throwable) {

        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .ifPresent(process -> process.gatewayFailed(messageOf(throwable)));
    }

    /**
     * 로컬 환불 반영 실패 상태를 기록한다.
     */
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
            Payment payment
    ) {

        if (paymentRefundProcessId == null) {
            return;
        }

        PaymentRefundProcess process = findById(paymentRefundProcessId);
        savePaymentRefundHistory(process, payment);
        process.localSucceeded();
    }

    /**
     * 결제 환불 처리 처리에서 이전 요청의 처리 결과를 복구한다.
     */
    @Transactional
    public CancelReservationResponse recoverPreviousRequest(Long paymentRefundProcessId) {

        PaymentRefundProcessResponse completed = completeLocal(paymentRefundProcessId);

        return CancelReservationResponse.recovered(
                completed.getReservationId(), paymentRefundProcessId, parseTicketIds(completed.getSelectedTicketIds())
        );
    }

    /**
     * 결제 환불 처리 처리에서 로컬 처리 결과를 완료 상태로 반영한다.
     */
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

    /**
     * 게이트웨이 환불 처리 시도를 시작한다.
     */
    @Transactional
    public PaymentRefundProcessGatewayAttempt startGatewayAttempt(
            Payment payment,
            List<Ticket> selectedTickets,
            boolean fullCancellation,
            RefundAccountRequest refundAccount,
            PaymentRefundRequestContext requestContext
    ) {

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

    /**
     * 동일한 기존 요청의 처리를 재개한다.
     */
    private PaymentRefundProcessGatewayAttempt resumeSameRequest(
            PaymentRefundProcess previous,
            Payment payment,
            List<Ticket> selectedTickets,
            boolean fullCancellation,
            RefundAccountRequest refundAccount
    ) {

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

    /**
     * 변경된 환불 요청을 새 처리 시도로 준비한다.
     */
    private PaymentRefundProcessGatewayAttempt prepareChangedRequest(
            PaymentRefundProcess previous,
            Payment payment,
            List<Ticket> selectedTickets,
            boolean fullCancellation,
            RefundAccountRequest refundAccount,
            LocalDateTime requestedAt
    ) {

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

    /**
     * 게이트웨이 환불이 이미 성공했는지 확인한다.
     */
    private boolean hasGatewaySucceeded(PaymentRefundProcess process) {

        return process.getStatus() == PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
                || process.getStatus() == PaymentRefundProcessStatus.LOCAL_FAILED;
    }

    /**
     * 이전 환불 요청의 결과를 확인하고 후속 처리를 결정한다.
     */
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

    /**
     * 새 처리 시도를 생성한다.
     */
    private PaymentRefundProcessGatewayAttempt createNewAttempt(
            Payment payment,
            List<Ticket> selectedTickets,
            boolean full,
            RefundAccountRequest account,
            LocalDateTime requestedAt
    ) {

        Long processId = paymentRefundProcessCreator.createNewRequest(payment, selectedTickets, full, account, requestedAt);

        return loadCreatedAttempt(processId);
    }

    /**
     * 처리 시도 정보를 생성한다.
     */
    private PaymentRefundProcessGatewayAttempt createAttempt(
            Payment payment,
            List<Ticket> selectedTickets,
            boolean full,
            RefundAccountRequest account,
            CancellationRefundCalculation calculated,
            LocalDateTime capturedAt
    ) {

        Long processId = paymentRefundProcessCreator.copyOriginalRequest(
                payment, selectedTickets, full, account, calculated, capturedAt
        );

        return loadCreatedAttempt(processId);
    }

    /**
     * 생성된 처리 시도를 조회한다.
     */
    private PaymentRefundProcessGatewayAttempt loadCreatedAttempt(Long processId) {

        // B의 최초 기록은 A가 롤백돼도 남는다. 예매 잠금 아래 일반 조회한다.
        PaymentRefundProcess process = paymentRefundProcessJpaRepository.findById(processId)
                .orElseThrow(() -> new IllegalStateException("생성한 환불 요청을 찾을 수 없습니다."));

        trackRollback(process);

        return attempt(process, process.getRefundAmount() > 0 ? RefundAction.REQUEST_GATEWAY : RefundAction.APPLY_LOCAL);
    }

    /**
     * 새 취소 요청이 현재 예매와 결제 상태에 유효한지 검증한다.
     */
    private void validateNewCancellation(Reservation reservation, LocalDateTime requestedAt) {

        ReservationCancellationPolicy.validateNewRequest(requestedAt,
                reservation.getEvent() == null ? null : reservation.getEvent().getCancelDeadlineAt());
    }

    /**
     * 두 요청의 처리 대상과 내용이 같은지 확인한다.
     */
    private boolean sameRequest(
            PaymentRefundProcess process,
            List<Ticket> tickets,
            boolean full,
            RefundAccountRequest account
    ) {

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

    /**
     * 환불 처리 시도를 실행한다.
     */
    private PaymentRefundProcessGatewayAttempt attempt(PaymentRefundProcess process, RefundAction action) {

        return PaymentRefundProcessGatewayAttempt.builder()
                .paymentRefundProcessId(process.getPaymentRefundProcessId())
                .action(action)
                .calculation(calculation(process.getRefund().getSnapshot()))
                .build();
    }

    /**
     * 환불 계산 정보를 구성한다.
     */
    private CancellationRefundCalculation calculation(PaymentRefundSnapshot snapshot) {

        return CancellationRefundCalculation.builder()
                .refundAmount(snapshot.getRefundAmount())
                .cancellationFeeAmount(snapshot.getCancellationFeeAmount())
                .deliveryRefundAmount(snapshot.getDeliveryRefundAmount())
                .discountDeductionAmount(snapshot.getDiscountAmount())
                .restoreCoupon(snapshot.isRestoreCoupon())
                .build();
    }

    /**
     * 트랜잭션 롤백 시 실패 정보를 기록하도록 등록한다.
     */
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

    /**
     * 예외에서 기록할 오류 메시지를 추출한다.
     */
    private String messageOf(Throwable throwable) {

        if (throwable == null) {
            return null;
        }
        return throwable.getMessage() != null
                ? throwable.getMessage()
                : throwable.getClass().getSimpleName();
    }

    /**
     * 결제 환불 처리 정보를 식별자로 조회한다.
     */
    private PaymentRefundProcess findById(Long paymentRefundProcessId) {

        return paymentRefundProcessJpaRepository
                .findById(paymentRefundProcessId)
                .orElseThrow(() -> new IllegalArgumentException("환불 처리 현황을 찾을 수 없습니다."));
    }

    /**
     * 결제 환불 처리 처리에서 로컬 처리 결과를 완료 상태로 반영한다.
     */
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

    /**
     * 결제 환불 처리 처리에서 환불 결과를 결제 정보에 반영한다.
     */
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

    /**
     * 결제 환불 결과를 이력으로 저장한다.
     */
    private void savePaymentRefundHistory(
            PaymentRefundProcess process,
            Payment payment
    ) {

        if (paymentRefundHistoryJpaRepository.existsByPaymentRefundProcess(process)) {
            throw new IllegalStateException("이미 환불 이력이 저장된 환불 처리입니다.");
        }

        paymentRefundHistoryJpaRepository.save(PaymentRefundHistory.create(process, payment));
    }

    /**
     * 환불 처리에 포함된 티켓을 조회한다.
     */
    private List<Ticket> selectProcessTickets(
            PaymentRefundProcess process,
            Reservation reservation
    ) {

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

    /**
     * 저장된 값을 티켓 식별자 목록으로 변환한다.
     */
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

    /**
     * 결제 환불 처리 처리에서 처리 대상 티켓을 취소한다.
     */
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

    /**
     * 취소된 티켓 범위에 맞춰 예매 취소 상태를 반영한다.
     */
    private void applyReservationCancelStatus(
            Reservation reservation,
            boolean fullCancellation,
            boolean restoreCouponOnCancel
    ) {

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
     * 취소한 예매에 사용된 쿠폰을 복원한다.
     */
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

    /**
     * 검색 조건을 JPA 조회 조건으로 구성한다.
     */
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

    /**
     * 요청한 정렬 조건을 구성한다.
     */
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

    private final class RefundRollbackSynchronization implements TransactionSynchronization {

        // 처리 진행 정보.
        private final PaymentRefundProcess process;
        // 예매 식별자.
        private final Long reservationId;
        // 동시성 검증에 사용할 예상 수정 시각.
        private final LocalDateTime expectedUpdatedAt;

        /**
         * 전달받은 값으로 환불 객체를 초기화한다.
         */
        private RefundRollbackSynchronization(PaymentRefundProcess process) {

            this.process = process;
            reservationId = process.getReservation().getReservationId();
            expectedUpdatedAt = process.getUpdatedAt();
        }

        /**
         * 환불 처리에서 트랜잭션 종료 결과에 따른 후속 처리를 수행한다.
         */
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
                log.error("환불 롤백 결과 기록 실패: processId={}, errorType={}",
                        record.processId(), e.getClass().getSimpleName());
            }
        }
    }
}
