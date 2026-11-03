package dev.bum.payment_gateway_service.dto.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    @NotNull
    @Positive
    private Long paymentRefundProcessId;

    // 결제 번호.
    @NotBlank
    private String paymentNo;

    // 환불받을 은행.
    @NotNull
    private BankCompany refundBankCompany;

    // 환불 계좌 번호.
    @NotBlank
    private String refundAccountNumber;

    // 환불 계좌 예금주.
    @NotBlank
    private String refundAccountHolder;

    // 환불 금액.
    @NotNull
    @Positive
    private BigDecimal refundAmount;
}
