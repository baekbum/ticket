package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayVirtualAccountStatusResponse(
        // 결제 번호.
        String paymentNo,

        // 은행 구분.
        BankCompany bankCompany,

        // 은행 이름.
        String bankName,

        // 계좌 번호.
        String accountNumber,

        // 입금자 이름.
        String depositorName,

        // 금액.
        BigDecimal amount,

        // 만료 시각.
        LocalDateTime expiresAt,

        // 입금 완료 시각.
        LocalDateTime depositedAt,

        // 가상계좌 상태.
        GatewayVirtualAccountStatus status
) {
}
