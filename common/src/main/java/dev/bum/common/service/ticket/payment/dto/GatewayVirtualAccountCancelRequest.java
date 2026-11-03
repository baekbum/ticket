package dev.bum.common.service.ticket.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** paymentNo로 식별하는 계좌의 전체 취소. 모든 재시도에서 동일한 계좌 정보를 사용한다. */
public record GatewayVirtualAccountCancelRequest(
        // 결제 번호.
        @NotBlank String paymentNo,

        // 은행 이름.
        @NotBlank String bankName,

        // 계좌 번호.
        @NotBlank String accountNumber,

        // 금액.
        @NotNull @DecimalMin("0") BigDecimal amount
) { }
