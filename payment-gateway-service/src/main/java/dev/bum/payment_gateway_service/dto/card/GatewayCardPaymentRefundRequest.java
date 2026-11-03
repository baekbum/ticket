package dev.bum.payment_gateway_service.dto.card;

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
public class GatewayCardPaymentRefundRequest {

    // 결제 환불 처리 식별자.
    @NotNull
    @Positive
    private Long paymentRefundProcessId;

    // 결제 번호.
    @NotBlank
    private String paymentNo;

    // 결제 거래 식별자.
    @NotBlank
    private String transactionId;

    // 환불 금액.
    @NotNull
    @Positive
    private BigDecimal refundAmount;
}
