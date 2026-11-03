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
public class GatewayCardPaymentRefundResponse {

    // 결제 번호.
    private String paymentNo;

    // 결제 거래 식별자.
    private String transactionId;

    // 누적 환불 금액.
    private BigDecimal refundedAmount;

    // 결제 환불 상태.
    private String status;

    // 처리 결과 메시지.
    private String message;
}
