package dev.bum.common.service.ticket.payment.dto;

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
@AllArgsConstructor
@NoArgsConstructor
public class VirtualAccountDepositCompleteRequest {

    @NotBlank
    private String paymentNo;

    @NotNull
    private BankCompany bankCompany;

    @NotBlank
    private String bankName;

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String depositorName;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private LocalDateTime depositedAt;
}
