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
public class GatewayVirtualAccountIssueResponse {

    private String paymentNo;
    private BankCompany bankCompany;
    private String bankName;
    private String accountNumber;
    private String depositorName;
    private BigDecimal amount;
    private LocalDateTime expiresAt;
    private Boolean issued;
    private String message;
}
