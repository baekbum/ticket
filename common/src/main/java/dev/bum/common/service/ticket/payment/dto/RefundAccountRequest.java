package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefundAccountRequest {

    @NotNull
    private BankCompany bankCompany;

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String accountHolder;
}
