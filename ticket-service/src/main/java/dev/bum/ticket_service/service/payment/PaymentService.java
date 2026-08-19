package dev.bum.ticket_service.service.payment;

import dev.bum.common.kafka.payment.VirtualAccountDepositCompletedEvent;
import dev.bum.common.service.ticket.payment.dto.CardPaymentApproveRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositRequest;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssuedRequest;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssueRequest;
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
import dev.bum.ticket_service.service.seat.SeatCacheService;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    private static final int MAX_ACCOUNT_ISSUE_ATTEMPTS = 5;

    private final PaymentJpaRepository paymentJpaRepository;
    private final TicketRepository ticketRepository;
    private final SeatCacheService seatCacheService;
    private final MockCardAuthorizationService mockCardAuthorizationService;
    private final MockVirtualAccountIssueService mockVirtualAccountIssueService;

    @AuditLog(action = "CARD_PAYMENT_APPROVE", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.approve-card", contextualName = "ticket payment approve card")
    public PaymentResponse approveCard(String currentUserId, CardPaymentApproveRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        Reservation reservation = payment.getReservation();

        validatePaymentOwner(currentUserId, reservation);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment.toResponse();
        }

        validateCardPaymentReady(payment);

        if (!mockCardAuthorizationService.approve(request)) {
            throw new IllegalArgumentException("카드 정보가 일치하지 않습니다.");
        }

        return completePayment(payment, null);
    }

    @AuditLog(action = "CARD_PAYMENT_COMPLETE_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.complete-card-from-gateway", contextualName = "ticket payment complete card from gateway")
    public PaymentResponse completeCardFromGateway(CardPaymentCompleteRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        Reservation reservation = payment.getReservation();

        validateGatewayCardCompletion(payment, reservation, request);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment.toResponse();
        }

        return completePayment(payment, LocalDateTime.now());
    }

    @AuditLog(action = "VIRTUAL_ACCOUNT_ISSUED_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.apply-virtual-account-issued", contextualName = "ticket payment apply virtual account issued")
    public PaymentResponse applyVirtualAccountIssued(VirtualAccountIssuedRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        validateGatewayVirtualAccountIssuedBasics(payment, request);
        if (payment.getStatus() == PaymentStatus.WAITING_DEPOSIT) {
            return payment.toResponse();
        }

        validateGatewayVirtualAccountIssuedReady(payment);
        PaymentStatus beforePaymentStatus = payment.getStatus();
        payment.waitDeposit(request.getBankName(), request.getAccountNumber(), request.getExpiresAt());
        AuditDataMapper.setFieldChange("status", beforePaymentStatus, payment.getStatus());

        return payment.toResponse();
    }

    @AuditLog(action = "VIRTUAL_ACCOUNT_ISSUE", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.issue-virtual-account", contextualName = "ticket payment issue virtual account")
    public PaymentResponse issueVirtualAccount(String currentUserId, VirtualAccountIssueRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        Reservation reservation = payment.getReservation();

        validatePaymentOwner(currentUserId, reservation);
        if (payment.getStatus() == PaymentStatus.WAITING_DEPOSIT) {
            return payment.toResponse();
        }

        validateBankTransferPaymentReady(payment);

        PaymentStatus beforePaymentStatus = payment.getStatus();
        MockVirtualAccountIssueService.VirtualAccount virtualAccount = issueUniqueVirtualAccount(request.getBankCode());

        payment.waitDeposit(
                virtualAccount.getBankName(),
                virtualAccount.getAccountNumber(),
                virtualAccount.getExpiresAt()
        );
        AuditDataMapper.setFieldChange("status", beforePaymentStatus, payment.getStatus());

        return payment.toResponse();
    }

    @AuditLog(action = "VIRTUAL_ACCOUNT_DEPOSIT", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.deposit-virtual-account", contextualName = "ticket payment deposit virtual account")
    public PaymentResponse depositVirtualAccount(VirtualAccountDepositRequest request) {
        Payment payment = paymentJpaRepository.findByAccountNumberForUpdate(request.getAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("입금 계좌 정보를 찾을 수 없습니다."));

        validateVirtualAccountDeposit(payment, request);

        return completePayment(payment, null, request.getDepositorName());
    }

    @AuditLog(action = "VIRTUAL_ACCOUNT_DEPOSIT_COMPLETED_FROM_GATEWAY", targetType = "PAYMENT")
    @Observed(name = "ticket.payment.complete-virtual-account-deposit-from-gateway", contextualName = "ticket payment complete virtual account deposit from gateway")
    public PaymentResponse completeVirtualAccountDepositFromGateway(VirtualAccountDepositCompletedEvent event) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(event.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        validateGatewayVirtualAccountDeposit(payment, event);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment.toResponse();
        }

        return completePayment(payment, event.getDepositedAt(), event.getDepositorName());
    }

    /**
     * 카드 승인 또는 무통장 입금 확인 이후 결제를 최종 완료 처리한다.
     * 결제, 예약, 티켓, 좌석 상태를 같은 트랜잭션에서 확정한다.
     */
    private PaymentResponse completePayment(Payment payment, LocalDateTime paidAt) {
        return completePayment(payment, paidAt, null);
    }

    private PaymentResponse completePayment(Payment payment, LocalDateTime paidAt, String depositorName) {
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

        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER && depositorName != null) {
            payment.completeDeposit(depositorName, paidAt);
        } else {
            payment.complete(paidAt);
        }

        AuditDataMapper.setFieldChange("status", beforePaymentStatus, payment.getStatus());

        reservation.paid();
        for (Ticket ticket : tickets) {
            ticket.paid();
            ticket.getSeat().reserved();
        }

        seatCacheService.syncReservedSeatsAfterCommit(seats);
        // 현재는 결제 완료 이벤트를 소비하는 consumer가 없으므로 Kafka 발행을 비활성화한다.
        // 후속 알림/정산/배송 이벤트 consumer를 붙일 때 PaymentEventProducer 호출을 다시 활성화한다.

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

    private void validateGatewayCardCompletion(
            Payment payment,
            Reservation reservation,
            CardPaymentCompleteRequest request
    ) {
        if (reservation == null || !request.getUserId().equals(reservation.getUserId())) {
            throw new AccessDeniedException("다른 사용자의 결제 완료 요청입니다.");
        }
        if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("카드 결제 요청이 아닙니다.");
        }
        if (BigDecimal.valueOf(payment.getAmount()).compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalArgumentException("카드 결제 완료 처리할 수 없는 상태입니다.");
        }
        validatePaymentNotExpired(payment);
    }

    private void validateGatewayVirtualAccountIssuedBasics(Payment payment, VirtualAccountIssuedRequest request) {
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("무통장 입금 결제 요청이 아닙니다.");
        }
        if (BigDecimal.valueOf(payment.getAmount()).compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }
    }

    private void validateGatewayVirtualAccountIssuedReady(Payment payment) {
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalArgumentException("가상계좌 발급 정보를 반영할 수 없는 결제 상태입니다.");
        }
        validatePaymentNotExpired(payment);
    }

    private void validateCardPaymentReady(Payment payment) {
        if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("카드 결제 요청이 아닙니다.");
        }
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalArgumentException("카드 승인 처리할 수 없는 결제 상태입니다.");
        }
        validatePaymentNotExpired(payment);
    }

    private void validateBankTransferPaymentReady(Payment payment) {
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("무통장 입금 결제 요청이 아닙니다.");
        }
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalArgumentException("가상계좌를 발급할 수 없는 결제 상태입니다.");
        }
        validatePaymentNotExpired(payment);
    }

    private void validatePaymentNotExpired(Payment payment) {
        if (payment.getExpiresAt() != null && LocalDateTime.now().isAfter(payment.getExpiresAt())) {
            payment.expire();
            throw new IllegalArgumentException("결제 기한이 만료되었습니다.");
        }
    }

    private MockVirtualAccountIssueService.VirtualAccount issueUniqueVirtualAccount(String bankCode) {
        for (int attempt = 0; attempt < MAX_ACCOUNT_ISSUE_ATTEMPTS; attempt++) {
            MockVirtualAccountIssueService.VirtualAccount virtualAccount = mockVirtualAccountIssueService.issue(bankCode);
            if (!paymentJpaRepository.existsByAccountNumber(virtualAccount.getAccountNumber())) {
                return virtualAccount;
            }
        }

        throw new IllegalStateException("가상계좌 번호를 발급하지 못했습니다.");
    }

    private void validateVirtualAccountDeposit(Payment payment, VirtualAccountDepositRequest request) {
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("무통장 입금 결제 요청이 아닙니다.");
        }
        if (payment.getStatus() != PaymentStatus.WAITING_DEPOSIT) {
            throw new IllegalArgumentException("입금 처리할 수 없는 결제 상태입니다.");
        }
        if (payment.getExpiresAt() != null && LocalDateTime.now().isAfter(payment.getExpiresAt())) {
            payment.expire();
            throw new IllegalArgumentException("입금 기한이 만료되었습니다.");
        }
        if (!payment.getAmount().equals(request.getAmount())) {
            throw new IllegalArgumentException("입금 금액이 일치하지 않습니다.");
        }
    }

    private void validateGatewayVirtualAccountDeposit(Payment payment, VirtualAccountDepositCompletedEvent event) {
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("무통장 입금 결제 요청이 아닙니다.");
        }
        if (BigDecimal.valueOf(payment.getAmount()).compareTo(event.getAmount()) != 0) {
            throw new IllegalArgumentException("입금 금액이 일치하지 않습니다.");
        }
        if (StringUtils.hasText(payment.getAccountNumber())
                && !payment.getAccountNumber().equals(event.getAccountNumber())) {
            throw new IllegalArgumentException("입금 계좌번호가 일치하지 않습니다.");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.WAITING_DEPOSIT) {
            throw new IllegalArgumentException("입금 처리할 수 없는 결제 상태입니다.");
        }
        if (payment.getExpiresAt() != null && event.getDepositedAt() != null && event.getDepositedAt().isAfter(payment.getExpiresAt())) {
            payment.expire();
            throw new IllegalArgumentException("입금 기한이 만료되었습니다.");
        }
    }

}
