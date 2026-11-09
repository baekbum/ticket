package dev.bum.payment_gateway_service.dto.financial;

import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import java.math.BigDecimal;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelResponse;

public record FinancialVirtualAccountCancelResponse(
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
) implements FinancialPaymentResponse {

    // 기존 금융 도메인의 처리 결과를 HTTP 경계 전용 DTO로 복사한다.
    public static FinancialVirtualAccountCancelResponse from(GatewayVirtualAccountCancelResponse source) {

        return new FinancialVirtualAccountCancelResponse(
                source.paymentNo(),
                source.bankName(),
                source.accountNumber(),
                source.amount(),
                source.status()
        );
    }
}
