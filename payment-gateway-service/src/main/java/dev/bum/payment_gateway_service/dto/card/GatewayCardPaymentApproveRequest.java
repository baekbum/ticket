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

    // 결제 번호.
    @NotBlank
    private String paymentNo;

    // 카드사.
    @NotNull
    private CardCompany cardCompany;

    // 카드 번호.
    @NotBlank
    private String cardNumber;

    // 카드 보안 코드.
    @NotBlank
    private String cvc;

    // 카드 비밀번호.
    @NotBlank
    private String cardPassword;

    // 금액.
    @NotNull
    @Positive
    private BigDecimal amount;
}
