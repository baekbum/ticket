package dev.bum.ticket_service.feign.paymentgateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayVirtualAccountIssueRequest {

    private String paymentNo;
    private BankCompany bankCompany;
    private BigDecimal amount;
    private LocalDateTime eventDateTime;
    private Boolean ticketPaymentApplyRequired;
}
