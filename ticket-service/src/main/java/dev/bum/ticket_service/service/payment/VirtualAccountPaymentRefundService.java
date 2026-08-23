package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.feign.paymentgateway.GatewayVirtualAccountRefundRequest;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@Transactional
@RequiredArgsConstructor
public class VirtualAccountPaymentRefundService {

    private final PaymentGatewayVirtualAccountClient paymentGatewayVirtualAccountClient;

    public void refundAll(
            Payment payment,
            BankCompany refundBankCompany,
            String refundAccountNumber,
            String refundAccountHolder
    ) {
        validateRefundableVirtualAccountPayment(payment, refundBankCompany, refundAccountNumber, refundAccountHolder);
        paymentGatewayVirtualAccountClient.refund(
                GatewayVirtualAccountRefundRequest.builder()
                        .paymentNo(payment.getPaymentNo())
                        .refundBankCompany(refundBankCompany)
                        .refundAccountNumber(refundAccountNumber)
                        .refundAccountHolder(refundAccountHolder)
                        .refundAmount(BigDecimal.valueOf(payment.getRefundableAmount()))
                        .build()
        );
        payment.refund();
    }

    public void refundPartial(
            Payment payment,
            int refundAmount,
            BankCompany refundBankCompany,
            String refundAccountNumber,
            String refundAccountHolder
    ) {
        validateRefundableVirtualAccountPayment(payment, refundBankCompany, refundAccountNumber, refundAccountHolder);
        paymentGatewayVirtualAccountClient.refund(
                GatewayVirtualAccountRefundRequest.builder()
                        .paymentNo(payment.getPaymentNo())
                        .refundBankCompany(refundBankCompany)
                        .refundAccountNumber(refundAccountNumber)
                        .refundAccountHolder(refundAccountHolder)
                        .refundAmount(BigDecimal.valueOf(refundAmount))
                        .build()
        );
        payment.partialRefund(refundAmount);
    }

    private void validateRefundableVirtualAccountPayment(
            Payment payment,
            BankCompany refundBankCompany,
            String refundAccountNumber,
            String refundAccountHolder
    ) {
        if (payment == null) {
            throw new IllegalArgumentException("결제 정보를 찾을 수 없습니다.");
        }
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("무통장 결제가 아닙니다.");
        }
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalArgumentException("환불할 수 없는 결제 상태입니다.");
        }
        if (refundBankCompany == null) {
            throw new IllegalArgumentException("환불 은행을 입력해야 합니다.");
        }
        if (!StringUtils.hasText(refundAccountNumber)) {
            throw new IllegalArgumentException("환불 계좌번호를 입력해야 합니다.");
        }
        if (!StringUtils.hasText(refundAccountHolder)) {
            throw new IllegalArgumentException("환불 계좌 예금주명을 입력해야 합니다.");
        }
        if (payment.getRefundableAmount() <= 0) {
            throw new IllegalArgumentException("환불 가능한 금액이 없습니다.");
        }
    }
}
