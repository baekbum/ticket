package dev.bum.common.service.paymentgateway.card;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayCardMethodChangeRequest(
        // 인증된 가맹점과 교차 검증하는 코드.
        Long merchantId,

        // 원거래의 구매자.
        @NotBlank String buyerReference,

        // 바꾸지 않는 원거래 금액.
        @NotNull @Positive BigDecimal amount,

        // 최초 전환 요청에서 고정하는 발급 은행.
        @NotNull BankCompany bankCompany,

        // 가맹점이 계산해 고정한 일반적인 입금 기한.
        @NotNull LocalDateTime expiresAt
) { }
