package dev.bum.payment_gateway_service.dto.financial;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import java.math.BigDecimal;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundResponse;

public record FinancialVirtualAccountRefundResponse(
        // 결제 번호.
        String paymentNo,

        // 환불받을 은행.
        BankCompany refundBankCompany,

        // 환불 계좌 번호.
        String refundAccountNumber,

        // 환불 계좌 예금주.
        String refundAccountHolder,

        // 누적 환불 금액.
        BigDecimal refundedAmount,

        // 처리 결과 메시지.
        String message
) implements FinancialPaymentResponse {

    // 기존 금융 도메인의 처리 결과를 HTTP 경계 전용 DTO로 복사한다.
    public static FinancialVirtualAccountRefundResponse from(GatewayVirtualAccountRefundResponse source) {

        return new FinancialVirtualAccountRefundResponse(
                source.getPaymentNo(),
                source.getRefundBankCompany(),
                source.getRefundAccountNumber(),
                source.getRefundAccountHolder(),
                source.getRefundedAmount(),
                source.getMessage()
        );
    }
}
