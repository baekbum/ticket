package dev.bum.ticket_service.feign.paymentgateway;

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
public class GatewayVirtualAccountRefundRequest {

    // 결제 환불 처리 식별자.
    private Long paymentRefundProcessId;

    // 결제 번호.
    private String paymentNo;

    // 환불받을 은행.
    private BankCompany refundBankCompany;

    // 환불 계좌 번호.
    private String refundAccountNumber;

    // 환불 계좌 예금주.
    private String refundAccountHolder;

    // 환불 금액.
    private BigDecimal refundAmount;
}
