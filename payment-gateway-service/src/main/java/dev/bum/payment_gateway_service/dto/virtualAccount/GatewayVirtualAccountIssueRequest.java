package dev.bum.payment_gateway_service.dto.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
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
public class GatewayVirtualAccountIssueRequest {

    @NotBlank
    private String paymentNo;

    @NotNull
    private BankCompany bankCompany;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private LocalDateTime eventDateTime;
}
