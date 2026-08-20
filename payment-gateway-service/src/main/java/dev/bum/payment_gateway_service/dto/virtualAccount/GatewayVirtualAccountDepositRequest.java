package dev.bum.payment_gateway_service.dto.virtualAccount;

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
@NoArgsConstructor
@AllArgsConstructor
public class GatewayVirtualAccountDepositRequest {

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String depositorName;

    @NotNull
    @Positive
    private BigDecimal amount;

    private LocalDateTime depositedAt;
}
