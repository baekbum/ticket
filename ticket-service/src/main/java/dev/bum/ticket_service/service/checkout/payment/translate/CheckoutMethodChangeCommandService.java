package dev.bum.ticket_service.service.checkout.payment.translate;

import dev.bum.common.service.paymentgateway.payment.GatewayBankMethodChangeResponse;

import dev.bum.common.service.paymentgateway.payment.GatewayBankMethodChangeRequest;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class CheckoutMethodChangeCommandService {

    private final VirtualAccountPaymentLockService locks;

    public PaymentResponse confirmCardToBank(String userId, String paymentNo, GatewayCardMethodChangeRequest request, GatewayCardMethodChangeResponse response) {

        Payment payment = owned(userId, paymentNo);

        if (response == null || !Objects.equals(request.merchantId(), response.merchantId())
                || !Objects.equals(payment.getPgPaymentId(), response.pgPaymentId())
                || !paymentNo.equals(response.paymentNo()) || !userId.equals(response.buyerReference())
                || response.amount() == null || BigDecimal.valueOf(payment.getAmount()).compareTo(response.amount()) != 0) {
            throw new IllegalStateException("결제수단 전환의 PG 원거래 응답이 일치하지 않습니다.");
        }

        if ("SWITCHED".equals(response.outcome())) {
            if (response.paymentMethod() != PaymentMethod.BANK_TRANSFER || response.methodRevision() != 1
                    || request.bankCompany() != response.bankCompany() || !request.expiresAt().equals(response.expiresAt())
                    || response.approval() != null) {
                throw new IllegalStateException("최초 무통장 전환 조건과 PG 결과가 다릅니다.");
            }

            if (payment.getMethod() == PaymentMethod.CREDIT_CARD) {
                payment.switchGatewayToBank(response.bankCompany(), response.expiresAt());
            } else if (payment.getMethod() != PaymentMethod.BANK_TRANSFER
                    || payment.getGatewayBankCompany() != response.bankCompany()
                    || !payment.getGatewayExpiresAt().equals(response.expiresAt())) {
                throw new IllegalStateException("이미 반영된 결제수단 전환 조건과 다릅니다.");
            }
        } else {
            if (!"APPROVED".equals(response.outcome()) || response.paymentMethod() != PaymentMethod.CREDIT_CARD
                    || response.approval() == null || payment.getMethod() != PaymentMethod.CREDIT_CARD
                    || response.bankCompany() != null || response.methodRevision() != 0
                    || !Objects.equals(payment.getGatewayExpiresAt(), response.expiresAt())
                    || !response.pgPaymentId().equals(response.approval().pgPaymentId())
                    || !response.merchantId().equals(response.approval().merchantId())
                    || !paymentNo.equals(response.approval().merchantPaymentNo())
                    || !userId.equals(response.approval().buyerReference()) || response.approval().amount() == null
                    || response.amount().compareTo(response.approval().amount()) != 0) {
                throw new IllegalStateException("카드 승인 복구 응답을 확인할 수 없습니다.");
            }
        }

        return payment.toResponse();
    }

    public PaymentResponse confirmBankToCard(String userId, String paymentNo,
            GatewayBankMethodChangeRequest request,
            GatewayBankMethodChangeResponse response) {

        Payment payment = owned(userId, paymentNo);

        if (response == null || !Objects.equals(request.merchantId(), response.merchantId())
                || !Objects.equals(payment.getPgPaymentId(), response.pgPaymentId())
                || !paymentNo.equals(response.paymentNo()) || !userId.equals(response.buyerReference())
                || response.amount() == null || BigDecimal.valueOf(payment.getAmount()).compareTo(response.amount()) != 0
                || response.paymentMethod() != PaymentMethod.CREDIT_CARD || response.methodRevision() != 1
                || !request.expiresAt().equals(response.expiresAt())) {
            throw new IllegalStateException("카드 전환의 최초 요청과 PG 응답이 일치하지 않습니다.");
        }

        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
            payment.switchGatewayToCard(response.expiresAt());
        } else if (payment.getMethod() != PaymentMethod.CREDIT_CARD
                || !Objects.equals(payment.getGatewayExpiresAt(), response.expiresAt())) {
            throw new IllegalStateException("이미 반영된 카드 전환 조건과 다릅니다.");
        }

        return payment.toResponse();
    }

    public PaymentResponse findPayment(String userId, String paymentNo) {

        return owned(userId, paymentNo).toResponse();
    }

    private Payment owned(String userId, String paymentNo) {

        Payment payment = locks.lock(paymentNo);

        if (userId == null || !userId.equals(payment.getReservation().getUserId())) {
            throw new AccessDeniedException("본인 결제만 변경할 수 있습니다.");
        }

        return payment;
    }
}
