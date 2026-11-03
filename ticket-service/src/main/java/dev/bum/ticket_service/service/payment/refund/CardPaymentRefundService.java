package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.feign.paymentgateway.GatewayCardPaymentRefundRequest;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@Transactional
@RequiredArgsConstructor
public class CardPaymentRefundService {

    // 결제 게이트웨이 연동 API를 호출하는 클라이언트.
    private final PaymentGatewayCardClient paymentGatewayCardClient;

    /**
     * 결제 환불을 전액 환불 결과를 반영한다.
     */
    public int refundAll(Payment payment, Long paymentRefundProcessId) {

        validateRefundableCardPayment(payment);

        int refundAmount = payment.getRefundableAmount();
        paymentGatewayCardClient.refund(
                GatewayCardPaymentRefundRequest.builder()
                        .paymentRefundProcessId(paymentRefundProcessId)
                        .paymentNo(payment.getPaymentNo())
                        .transactionId(payment.getCardTransactionId())
                        .refundAmount(BigDecimal.valueOf(refundAmount))
                        .build()
        );
        payment.refund();
        return refundAmount;
    }

    /**
     * 결제 환불을 부분 환불 결과를 반영한다.
     */
    public int refundPartial(Payment payment, int refundAmount, Long paymentRefundProcessId) {

        validateRefundableCardPayment(payment);

        paymentGatewayCardClient.refund(
                GatewayCardPaymentRefundRequest.builder()
                        .paymentRefundProcessId(paymentRefundProcessId)
                        .paymentNo(payment.getPaymentNo())
                        .transactionId(payment.getCardTransactionId())
                        .refundAmount(BigDecimal.valueOf(refundAmount))
                        .build()
        );
        payment.partialRefund(refundAmount);
        return refundAmount;
    }

    /**
     * 카드 결제가 환불 가능한 상태인지 검증한다.
     */
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
