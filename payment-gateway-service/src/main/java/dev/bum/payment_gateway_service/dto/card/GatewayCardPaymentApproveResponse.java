package dev.bum.payment_gateway_service.dto.card;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayCardPaymentApproveResponse {

    private String paymentNo;
    private String transactionId;
    private String userId;
    private CardCompany cardCompany;
    private String maskedCardNumber;
    private BigDecimal approvedAmount;
    private BigDecimal currentMonthUsedAmount;
    private BigDecimal limitAmount;
    private Boolean approved;
    private String message;
}
