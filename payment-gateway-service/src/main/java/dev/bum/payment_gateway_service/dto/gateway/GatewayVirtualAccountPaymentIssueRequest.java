package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record GatewayVirtualAccountPaymentIssueRequest(
        // 선택적 일치 검증 값. 인증에 사용하지 않는다.
        @Positive Long merchantId,

        // 준비 시 가맹점이 확인한 예매자와 일치해야 한다.
        @NotBlank @Size(max = 50) String buyerReference,

        // 원거래 금액과 일치해야 한다. 실제 은행 요청에는 저장된 금액을 사용한다.
        @NotNull @Positive BigDecimal amount,

        // 준비 시 저장한 은행과 일치해야 한다.
        @NotNull BankCompany bankCompany
) { }
