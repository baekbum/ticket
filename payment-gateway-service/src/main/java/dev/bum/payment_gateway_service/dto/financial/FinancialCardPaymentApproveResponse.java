package dev.bum.payment_gateway_service.dto.financial;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import java.math.BigDecimal;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveResponse;

public record FinancialCardPaymentApproveResponse(
        // 결제 번호.
        String paymentNo,

        // 결제 거래 식별자.
        String transactionId,

        // 사용자 ID.
        String userId,

        // 카드사.
        CardCompany cardCompany,

        // 마스킹한 카드 번호.
        String maskedCardNumber,

        // 승인된 결제 금액.
        BigDecimal approvedAmount,

        // 이번 달 카드 사용 금액.
        BigDecimal currentMonthUsedAmount,

        // 카드 사용 한도.
        BigDecimal limitAmount,

        // 카드 승인 여부.
        Boolean approved,

        // 처리 결과 메시지.
        String message
) implements FinancialPaymentResponse {

    // 기존 금융 도메인의 처리 결과를 HTTP 경계 전용 DTO로 복사한다.
    public static FinancialCardPaymentApproveResponse from(GatewayCardPaymentApproveResponse source) {

        return new FinancialCardPaymentApproveResponse(
                source.getPaymentNo(),
                source.getTransactionId(),
                source.getUserId(),
                source.getCardCompany(),
                source.getMaskedCardNumber(),
                source.getApprovedAmount(),
                source.getCurrentMonthUsedAmount(),
                source.getLimitAmount(),
                source.getApproved(),
                source.getMessage()
        );
    }
}
