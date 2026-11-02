package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import java.math.BigDecimal;

public record GatewayVirtualAccountCancelResponse(
        String paymentNo, String bankName, String accountNumber,
        BigDecimal amount, GatewayVirtualAccountStatus status
) { }
