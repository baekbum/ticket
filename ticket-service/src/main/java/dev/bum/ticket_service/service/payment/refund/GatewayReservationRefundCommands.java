package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.reservation.policy.ReservationCancellationPolicy;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.exception.reservation.GatewayRefundRequestException;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayRefundApplicationClient;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.payment.refund.*;
import dev.bum.ticket_service.jpa.reservation.reservation.*;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.ticket.*;
import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator;
import dev.bum.ticket_service.service.reservation.reservation.CancellationTicketValidator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GatewayReservationRefundCommands {

    // 사용자 환불·철회의 공통 NOWAIT 잠금 대상.
    private final ReservationJpaRepository reservations;

    // 신규 PG 원거래의 결제 상태를 확인한다.
    private final PaymentJpaRepository payments;

    // 최초 대상 티켓과 가격을 계산한다.
    private final TicketJpaRepository tickets;

    // 기존 할인·수수료 계산을 재사용한다.
    private final ReservationDiscountJpaRepository discounts;

    // 최초 스냅샷과 동일 요청 키를 커밋한다.
    private final PaymentRefundProcessJpaRepository processes;

    // 기존 좌석·쿠폰·매수·Payment의 원자적 정리를 재사용한다.
    private final PaymentRefundProcessService completion;

    // 계좌 원문은 암호문으로만 재시도에 보관한다.
    private final RefundAccountCipher cipher;

    // Ticksy 자체 재시도가 없는 PG 클라이언트.
    private final PaymentGatewayRefundApplicationClient gateway;

    // 최초 수수료 계산 시각을 고정한다.
    private final Clock clock;

    // native NOWAIT 조회 직전 이후의 최신 행 상태를 보장한다.
    private final EntityManager entityManager;

    // 요청자가 선택할 수 없는 서버의 가맹점 코드.
    @Value("${app.payment-gateway.merchant-id:1}")
    private long merchantId;

    /**
     * 완료 요청부터 찾아 재응답하고, 예매당 하나의 미완료 신청과 암호화 스냅샷을 먼저 커밋한다.
     */
    public Long prepare(String userId, long reservationId, CancelReservationRequest request) {

        Reservation reservation = lock(userId, reservationId);
        Payment payment = payment(reservation);
        List<Long> ids = request.getSelectedTicketIdList();

        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("중복 없는 환불 대상 티켓을 선택해주세요.");
        }

        if (request.getEventId() != null && !request.getEventId().equals(reservation.getEvent().getEventId())) {
            throw new IllegalArgumentException("환불 예매의 공연이 일치하지 않습니다.");
        }

        RefundAccountRequest account = payment.getMethod() == PaymentMethod.BANK_TRANSFER
                ? account(request.getRefundAccount()) : cardAccount(request.getRefundAccount());
        List<PaymentRefundProcess> previous = processes.findByRefundPaymentReservationOrderByPaymentRefundProcessIdDesc(reservation);

        for (PaymentRefundProcess process : previous) {
            if (process.getStatus() == PaymentRefundProcessStatus.LOCAL_SUCCEEDED && sameTickets(process, ids)) {
                process.validateRefundAccount(account);
                return process.getPaymentRefundProcessId();
            }
        }

        for (PaymentRefundProcess process : previous) {
            if (active(process)) {
                if (!sameTickets(process, ids) || !sameAccount(process, account)) {
                    throw conflict(process);
                }

                if (process.getStatus() == PaymentRefundProcessStatus.WITHDRAW_REQUESTED) {
                    throw conflict(process);
                }

                return process.getPaymentRefundProcessId();
            }
        }

        if ((reservation.getStatus() != ReservationStatus.PAID && reservation.getStatus() != ReservationStatus.PARTIALLY_CANCELLED)
                || (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED)) {
            throw new IllegalArgumentException("환불 가능한 예매가 아닙니다.");
        }

        List<Ticket> all = tickets.findByReservation(reservation);
        List<Ticket> available = all.stream().filter(t -> t.getStatus() == TicketStatus.PAID).toList();
        List<Ticket> selected = available.stream().filter(t -> ids.contains(t.getTicketId())).toList();

        if (selected.size() != ids.size()) {
            throw new IllegalArgumentException("예매의 결제 완료 티켓만 환불할 수 있습니다.");
        }

        CancellationTicketValidator.validate(reservation, selected);
        LocalDateTime capturedAt = LocalDateTime.now(clock);
        ReservationCancellationPolicy.validateNewRequest(capturedAt, reservation.getEvent().getCancelDeadlineAt());
        boolean full = selected.size() == available.size();
        CancellationFeeCalculator.CancellationRefundCalculation calculation = CancellationFeeCalculator.calculate(
                reservation, payment, available, selected, discounts.findByReservation(reservation), full, capturedAt);
        PaymentRefundProcess process = PaymentRefundProcess.create(payment, selected, calculation.refundAmount(),
                calculation.cancellationFeeAmount(), full, account, capturedAt, calculation.restoreCoupon());
        if (account != null) {
            process.getRefund().getSnapshot().captureAccountCiphertext(cipher.encrypt(account.getAccountNumber()));
        }

        return processes.saveAndFlush(process).getPaymentRefundProcessId();
    }

    /**
     * 철회 의도를 금융 HTTP 전에 커밋해 결과 유실 뒤 새 환불 실행으로 전환되지 않게 한다.
     */
    public void prepareWithdrawal(String userId, long reservationId, long processId) {

        Reservation reservation = lock(userId, reservationId);
        payment(reservation);
        ownedProcess(reservation, processId).withdrawalRequested();
    }

    /**
     * 합의한 대로 HTTP 동안 예매 NOWAIT 락을 유지한다. 최초 신청은 이미 별도 커밋돼 있다.
     */
    @Transactional(timeout = 95)
    public GatewayRefundActionResponse execute(
            String userId,
            long reservationId,
            long processId,
            boolean withdraw
    ) {

        Reservation reservation = lock(userId, reservationId);
        Payment payment = payment(reservation);
        PaymentRefundProcess process = ownedProcess(reservation, processId);

        if (process.getStatus() == PaymentRefundProcessStatus.LOCAL_SUCCEEDED) {
            return response(process, true);
        }

        if (process.getStatus() == PaymentRefundProcessStatus.WITHDRAWN) {
            return response(process, false);
        }

        if (process.getStatus() == PaymentRefundProcessStatus.SUPERSEDED) {
            throw new IllegalArgumentException("종료된 환불 신청입니다.");
        }

        if (!withdraw && process.getStatus() == PaymentRefundProcessStatus.WITHDRAW_REQUESTED) {
            throw conflict(process);
        }

        if (process.getStatus() == PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
                || process.getStatus() == PaymentRefundProcessStatus.LOCAL_FAILED) {
            completion.completeLocal(processId);

            return response(process, true);
        }

        if (process.getRefundAmount() == 0) {
            if (withdraw) process.withdrawn();
            else {
                process.gatewaySucceeded();
                completion.completeLocal(processId);
            }

            return response(process, withdraw);
        }

        PaymentRefundSnapshot snapshot = process.getRefund().getSnapshot();
        GatewayPaymentRefundRequest request = new GatewayPaymentRefundRequest(merchantId, reservation.getUserId(),
                BigDecimal.valueOf(snapshot.getRefundAmount()), snapshot.getRefundBankCompany(),
                payment.getMethod() == PaymentMethod.BANK_TRANSFER
                        ? cipher.decrypt(snapshot.getRefundAccountCiphertext()) : null, snapshot.getRefundAccountHolder());
        String key = Long.toString(processId);
        PaymentRefundResult result = withdraw ? gateway.withdraw(payment.getPgPaymentId(), key, request)
                : gateway.refund(payment.getPgPaymentId(), key, request);

        boolean matches = result != null && (payment.getMethod() == PaymentMethod.CREDIT_CARD
                ? result.matches(request.toCard(payment.getPgPaymentId(), key))
                : result.matches(request.toBank(payment.getPgPaymentId(), key)));

        if (!matches) {
            throw new GatewayRefundRequestException("GATEWAY_REFUND_UNCONFIRMED", 503,
                    "환불 결과를 확인하지 못했습니다. 같은 신청으로 다시 시도해주세요.", null);
        }

        if ("WITHDRAWN".equals(result.outcome())) process.withdrawn();
        else {
            process.gatewaySucceeded();
            completion.completeLocal(processId);
        }

        return response(process, withdraw);
    }

    /**
     * 완료·철회 이력도 소유자에게만 제공한다. 계좌 암호문과 원문은 DTO에 포함되지 않는다.
     */
    @Transactional(readOnly = true)
    public List<PaymentRefundProcessResponse> findApplications(String userId, long reservationId) {

        Reservation reservation = reservations.findById(reservationId).orElseThrow();
        validateOwner(userId, reservation);
        return processes.findByRefundPaymentReservationOrderByPaymentRefundProcessIdDesc(reservation).stream()
                .map(PaymentRefundProcess::toResponse).toList();
    }

    /**
     * 최신 예매 상태와 사용자 소유권을 공통 순서로 확인한다. 락 충돌은 상위 HTTP 경계에서 409로 변환한다.
     */
    private Reservation lock(String userId, long reservationId) {

        Reservation reservation = reservations.findByIdForUpdateNowait(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예매를 찾을 수 없습니다."));
        entityManager.refresh(reservation);
        validateOwner(userId, reservation);
        return reservation;
    }

    /**
     * 본문 userId를 신뢰하지 않고 인증된 사용자를 확인한다.
     */
    private void validateOwner(String userId, Reservation reservation) {

        if (userId == null || !userId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("본인 예매만 처리할 수 있습니다.");
        }
    }

    /**
     * 저장된 결제수단과 PG 연결로 신규 카드·무통장 거래를 확인한다.
     */
    private Payment payment(Reservation reservation) {

        Payment payment = payments.findByReservation(reservation).orElseThrow();

        boolean supported = payment.getMethod() == PaymentMethod.CREDIT_CARD
                || (payment.getMethod() == PaymentMethod.BANK_TRANSFER && payment.getGatewayBankCompany() != null);

        if (!supported || payment.getPgPaymentId() == null) {
            throw new IllegalArgumentException("신규 PG 카드·무통장 거래가 아닙니다.");
        }

        return payment;
    }

    /**
     * 요청 ID만으로 다른 예매의 환불을 재시도하거나 철회하지 못하게 한다.
     */
    private PaymentRefundProcess ownedProcess(Reservation reservation, long processId) {

        PaymentRefundProcess process = processes.findById(processId).orElseThrow();

        if (!process.getReservation().getReservationId().equals(reservation.getReservationId())) {
            throw new AccessDeniedException("예매의 환불 신청이 아닙니다.");
        }

        return process;
    }

    /**
     * 순서에 영향받지 않는 티켓 집합으로 요청을 식별한다.
     */
    private boolean sameTickets(PaymentRefundProcess process, List<Long> ids) {

        return process.getRefund().getSnapshot().getTickets().stream().map(PaymentRefundSnapshotTicket::getTicketId)
                .collect(Collectors.toSet()).equals(new HashSet<>(ids));
    }

    /**
     * 계좌 원문 대신 최초 스냅샷의 해시·은행·예금주와 비교한다.
     */
    private boolean sameAccount(PaymentRefundProcess process, RefundAccountRequest account) {

        try {
            process.validateRefundAccount(account);
            return true;
        } catch (IllegalArgumentException conflict) {
            return false;
        }
    }

    // 카드 환불은 원승인 카드로 처리한다. 브라우저가 다른 환불 계좌를 지정하지 못하게 한다.
    private RefundAccountRequest cardAccount(RefundAccountRequest input) {

        if (input != null) {
            throw new IllegalArgumentException("카드 환불에는 환불 계좌를 전달할 수 없습니다.");
        }

        return null;
    }

    /**
     * 입력 앞뒤 공백을 고정하고 부족한 계좌 정보를 거절한다.
     */
    private RefundAccountRequest account(RefundAccountRequest input) {

        if (input == null || input.getBankCompany() == null || input.getAccountNumber() == null
                || input.getAccountNumber().isBlank() || input.getAccountNumber().length() > 50
                || input.getAccountHolder() == null || input.getAccountHolder().isBlank() || input.getAccountHolder().length() > 50) {
            throw new IllegalArgumentException("환불받을 은행·계좌번호·예금주를 확인해주세요.");
        }

        return new RefundAccountRequest(input.getBankCompany(), input.getAccountNumber().strip(), input.getAccountHolder().strip());
    }

    /**
     * 철회·완료·기존 대체 상태는 새 신청을 차단하지 않는다.
     */
    private boolean active(PaymentRefundProcess process) {

        return process.getStatus() != PaymentRefundProcessStatus.LOCAL_SUCCEEDED
                && process.getStatus() != PaymentRefundProcessStatus.WITHDRAWN
                && process.getStatus() != PaymentRefundProcessStatus.SUPERSEDED;
    }

    /**
     * 미완료 신청 변경은 자동 교체하지 않고 최초 대상·금액을 함께 응답한다.
     */
    private GatewayRefundRequestException conflict(PaymentRefundProcess process) {

        return new GatewayRefundRequestException("REFUND_REQUEST_CONFLICT", 409,
                "진행 중인 환불 신청과 내용이 다릅니다. 기존 신청을 확인하거나 철회한 후 다시 신청해주세요.", process.toResponse());
    }

    /**
     * 트랜잭션이 커밋된 후 반환할 업무 결과를 구성한다. 이미 성공한 철회는 오류로 던지지 않는다.
     */
    private GatewayRefundActionResponse response(PaymentRefundProcess process, boolean recovery) {

        boolean success = process.getStatus() == PaymentRefundProcessStatus.LOCAL_SUCCEEDED;
        return new GatewayRefundActionResponse(success ? "SUCCEEDED" : "WITHDRAWN", process.toResponse(), success
                ? (recovery ? "이미 환불이 완료된 신청입니다. 환불 결과를 반영했습니다." : "환불이 완료되었습니다.")
                : "환불 신청을 철회했습니다. 다른 좌석으로 새로 신청할 수 있습니다.");
    }
}
