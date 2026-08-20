package dev.bum.common.service.ticket.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VirtualAccountIssuedRequest {

    @NotBlank
    private String paymentNo;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String bankName;

    @NotBlank
    private String accountNumber;

    @NotNull
    private LocalDateTime expiresAt;
}
