package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import java.math.BigDecimal;

public record GatewayVirtualAccountCancelResponse(
        // 결제 번호.
        String paymentNo,

        // 은행 이름.
        String bankName,

        // 계좌 번호.
        String accountNumber,

        // 금액.
        BigDecimal amount,

        // 가상계좌 상태.
        GatewayVirtualAccountStatus status
) { }
