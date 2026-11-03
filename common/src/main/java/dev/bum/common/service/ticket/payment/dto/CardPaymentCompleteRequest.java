package dev.bum.common.service.ticket.payment.dto;

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
@AllArgsConstructor
@NoArgsConstructor
public class CardPaymentCompleteRequest {

    // 결제 번호.
    @NotBlank
    private String paymentNo;

    // 사용자 ID.
    @NotBlank
    private String userId;

    // 금액.
    @NotNull
    @Positive
    private BigDecimal amount;

    // 결제 거래 식별자.
    @NotBlank
    private String transactionId;

    // 카드사.
    @NotNull
    private CardCompany cardCompany;

    // 마스킹한 카드 번호.
    @NotBlank
    private String maskedCardNumber;
}
