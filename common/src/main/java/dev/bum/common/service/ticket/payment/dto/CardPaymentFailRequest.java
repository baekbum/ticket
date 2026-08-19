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

    @NotBlank
    private String paymentNo;

    @NotBlank
    private String userId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String failureReason;
}
