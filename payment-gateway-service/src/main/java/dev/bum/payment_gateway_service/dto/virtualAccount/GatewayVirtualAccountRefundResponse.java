package dev.bum.payment_gateway_service.dto.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayVirtualAccountRefundResponse {

    // 결제 번호.
    private String paymentNo;

    // 환불받을 은행.
    private BankCompany refundBankCompany;

    // 환불 계좌 번호.
    private String refundAccountNumber;

    // 환불 계좌 예금주.
    private String refundAccountHolder;

    // 누적 환불 금액.
    private BigDecimal refundedAmount;

    // 처리 결과 메시지.
    private String message;
}
