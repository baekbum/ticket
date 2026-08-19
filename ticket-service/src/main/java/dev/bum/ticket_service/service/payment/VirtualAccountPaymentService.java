package dev.bum.ticket_service.service.payment;

import dev.bum.common.kafka.payment.VirtualAccountDepositCompletedEvent;
import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssuedRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class VirtualAccountPaymentService {

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentCompletionService paymentCompletionService;
    private final PaymentExpirationService paymentExpirationService;

    public PaymentResponse applyIssuedFromGateway(VirtualAccountIssuedRequest request) {
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

    public PaymentResponse completeDepositFromGateway(VirtualAccountDepositCompletedEvent event) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(event.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        validateGatewayVirtualAccountDeposit(payment, event);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment.toResponse();
        }

        return paymentCompletionService.completeDeposit(payment, event.getDepositedAt(), event.getDepositorName());
    }

    public PaymentResponse expireFromGateway(VirtualAccountExpiredEvent event) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(event.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        validateGatewayVirtualAccountExpiration(payment, event);
        if (payment.getStatus() == PaymentStatus.EXPIRED || payment.getStatus() == PaymentStatus.PAID) {
            return payment.toResponse();
        }

        paymentExpirationService.expire(payment);
        return payment.toResponse();
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

    private void validateGatewayVirtualAccountExpiration(Payment payment, VirtualAccountExpiredEvent event) {
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("무통장 입금 결제 요청이 아닙니다.");
        }
        if (BigDecimal.valueOf(payment.getAmount()).compareTo(event.getAmount()) != 0) {
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }
        if (StringUtils.hasText(payment.getAccountNumber())
                && !payment.getAccountNumber().equals(event.getAccountNumber())) {
            throw new IllegalArgumentException("입금 계좌번호가 일치하지 않습니다.");
        }
        if (payment.getStatus() == PaymentStatus.EXPIRED || payment.getStatus() == PaymentStatus.PAID) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.WAITING_DEPOSIT) {
            throw new IllegalArgumentException("만료 처리할 수 없는 결제 상태입니다.");
        }
    }

    private void validatePaymentNotExpired(Payment payment) {
        if (payment.getExpiresAt() != null && LocalDateTime.now().isAfter(payment.getExpiresAt())) {
            payment.expire();
            throw new IllegalArgumentException("결제 기한이 만료되었습니다.");
        }
    }
}
