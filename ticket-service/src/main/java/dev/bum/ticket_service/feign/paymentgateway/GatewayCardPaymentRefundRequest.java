package dev.bum.ticket_service.feign.paymentgateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayCardPaymentRefundRequest {

    // 결제 환불 처리 식별자.
    private Long paymentRefundProcessId;

    // 결제 번호.
    private String paymentNo;

    // 결제 거래 식별자.
    private String transactionId;

    // 환불 금액.
    private BigDecimal refundAmount;
}
