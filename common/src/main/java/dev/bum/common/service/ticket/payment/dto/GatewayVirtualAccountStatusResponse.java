package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayVirtualAccountStatusResponse(
        String paymentNo,
        BankCompany bankCompany,
        String bankName,
        String accountNumber,
        String depositorName,
        BigDecimal amount,
        LocalDateTime expiresAt,
        LocalDateTime depositedAt,
        GatewayVirtualAccountStatus status
) {
}
