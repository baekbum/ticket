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

    private String paymentNo;
    private String transactionId;
    private BigDecimal refundedAmount;
    private String status;
    private String message;
}
