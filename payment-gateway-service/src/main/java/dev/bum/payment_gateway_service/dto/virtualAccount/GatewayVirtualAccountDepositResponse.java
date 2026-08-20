package dev.bum.payment_gateway_service.dto.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
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
public class GatewayVirtualAccountDepositResponse {

    private String paymentNo;
    private BankCompany bankCompany;
    private String bankName;
    private String accountNumber;
    private String depositorName;
    private BigDecimal amount;
    private VirtualAccountPaymentStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime depositedAt;
    private String message;
}
