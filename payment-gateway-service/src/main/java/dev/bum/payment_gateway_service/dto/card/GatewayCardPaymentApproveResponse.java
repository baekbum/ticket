package dev.bum.payment_gateway_service.dto.card;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayCardPaymentApproveResponse {

    // 결제 번호.
    private String paymentNo;

    // 결제 거래 식별자.
    private String transactionId;

    // 사용자 ID.
    private String userId;

    // 카드사.
    private CardCompany cardCompany;

    // 마스킹한 카드 번호.
    private String maskedCardNumber;

    // 승인된 결제 금액.
    private BigDecimal approvedAmount;

    // 이번 달 카드 사용 금액.
    private BigDecimal currentMonthUsedAmount;

    // 카드 사용 한도.
    private BigDecimal limitAmount;

    // 카드 승인 여부.
    private Boolean approved;

    // 처리 결과 메시지.
    private String message;
}
