package dev.bum.payment_gateway_service.dto.card;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
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
public class GatewayCardPaymentApproveRequest {

    @NotBlank
    private String paymentNo;

    @NotNull
    private CardCompany cardCompany;

    @NotBlank
    private String cardNumber;

    @NotBlank
    private String cvc;

    @NotBlank
    private String cardPassword;

    @NotBlank
    private String customerName;

    @NotNull
    @Positive
    private BigDecimal amount;
}
