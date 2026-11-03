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

    // 계좌 번호.
    @NotBlank
    private String accountNumber;

    // 입금자 이름.
    @NotBlank
    private String depositorName;

    // 금액.
    @NotNull
    @Positive
    private BigDecimal amount;

    // 입금 완료 시각.
    private LocalDateTime depositedAt;
}
