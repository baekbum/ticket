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

    // 결제 번호.
    @NotBlank
    private String paymentNo;

    // 은행 구분.
    @NotNull
    private BankCompany bankCompany;

    // 은행 이름.
    @NotBlank
    private String bankName;

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
    @NotNull
    private LocalDateTime depositedAt;
}
