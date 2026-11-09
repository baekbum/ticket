package dev.bum.payment_gateway_service.dto.financial;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositResponse;

public record FinancialVirtualAccountDepositResponse(
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

        // 가상계좌 상태.
        VirtualAccountPaymentStatus status,

        // 만료 시각.
        LocalDateTime expiresAt,

        // 입금 완료 시각.
        LocalDateTime depositedAt,

        // 처리 결과 메시지.
        String message
) implements FinancialPaymentResponse {

    // 기존 금융 도메인의 처리 결과를 HTTP 경계 전용 DTO로 복사한다.
    public static FinancialVirtualAccountDepositResponse from(GatewayVirtualAccountDepositResponse source) {

        return new FinancialVirtualAccountDepositResponse(
                source.getPaymentNo(),
                source.getBankCompany(),
                source.getBankName(),
                source.getAccountNumber(),
                source.getDepositorName(),
                source.getAmount(),
                source.getStatus(),
                source.getExpiresAt(),
                source.getDepositedAt(),
                source.getMessage()
        );
    }
}
