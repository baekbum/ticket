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

    // 결제 번호.
    @NotBlank
    private String paymentNo;

    // 은행 구분.
    @NotNull
    private BankCompany bankCompany;

    // 금액.
    @NotNull
    @Positive
    private BigDecimal amount;

    // 공연 시작 일시.
    @NotNull
    private LocalDateTime eventDateTime;
}
