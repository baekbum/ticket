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

    // 은행 구분.
    @NotNull
    private BankCompany bankCompany;

    // 계좌 번호.
    @NotBlank
    private String accountNumber;

    // 계좌 예금주.
    @NotBlank
    private String accountHolder;
}
