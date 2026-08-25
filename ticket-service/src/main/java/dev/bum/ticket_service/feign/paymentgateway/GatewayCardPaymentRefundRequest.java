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

    private String paymentNo;
    private String transactionId;
    private BigDecimal refundAmount;
}
