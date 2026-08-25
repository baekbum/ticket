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

    @NotBlank
    private String paymentNo;

    @NotBlank
    private String transactionId;

    @NotNull
    @Positive
    private BigDecimal refundAmount;
}
