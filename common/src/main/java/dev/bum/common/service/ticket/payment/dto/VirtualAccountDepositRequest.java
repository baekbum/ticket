package dev.bum.common.service.ticket.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VirtualAccountDepositRequest {

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String depositorName;

    @NotNull
    @Positive
    private Integer amount;
}
