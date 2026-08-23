package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.feign.paymentgateway.GatewayCardPaymentRefundRequest;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.jpa.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@Transactional
@RequiredArgsConstructor
public class CardPaymentRefundService {

    private final PaymentGatewayCardClient paymentGatewayCardClient;

    public void refundAll(Payment payment) {
        validateRefundableCardPayment(payment);
        paymentGatewayCardClient.refund(
                GatewayCardPaymentRefundRequest.builder()
                        .paymentNo(payment.getPaymentNo())
                        .transactionId(payment.getCardTransactionId())
                        .refundAmount(BigDecimal.valueOf(payment.getRefundableAmount()))
                        .build()
        );
        payment.refund();
    }

    public void refundPartial(Payment payment, int refundAmount) {
        validateRefundableCardPayment(payment);
        paymentGatewayCardClient.refund(
                GatewayCardPaymentRefundRequest.builder()
                        .paymentNo(payment.getPaymentNo())
                        .transactionId(payment.getCardTransactionId())
                        .refundAmount(BigDecimal.valueOf(refundAmount))
                        .build()
        );
        payment.partialRefund(refundAmount);
    }

    private void validateRefundableCardPayment(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("결제 정보를 찾을 수 없습니다.");
        }
        if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("카드 결제가 아닙니다.");
        }
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalArgumentException("환불할 수 없는 결제 상태입니다.");
        }
        if (!StringUtils.hasText(payment.getCardTransactionId())) {
            throw new IllegalArgumentException("카드 거래번호가 없습니다.");
        }
        if (payment.getRefundableAmount() <= 0) {
            throw new IllegalArgumentException("환불 가능한 금액이 없습니다.");
        }
    }
}
