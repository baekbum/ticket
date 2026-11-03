package dev.bum.common.service.ticket.payment.dto;

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
public class CardPaymentFailRequest {

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

    // 실패 사유.
    @NotBlank
    private String failureReason;
}
