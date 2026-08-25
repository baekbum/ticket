package dev.bum.payment_gateway_service.dto.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayVirtualAccountRefundResponse {

    private String paymentNo;
    private BankCompany refundBankCompany;
    private String refundAccountNumber;
    private String refundAccountHolder;
    private BigDecimal refundedAmount;
    private String message;
}
