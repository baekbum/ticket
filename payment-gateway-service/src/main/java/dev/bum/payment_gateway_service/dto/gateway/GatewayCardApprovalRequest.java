package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record GatewayCardApprovalRequest(
        // 선택적인 principal 일치 검증 값. 인증 근거로 사용하지 않는다.
        @Positive Long merchantId,

        // 준비된 원거래의 예매자.
        @NotBlank @Size(max = 50) String buyerReference,

        // 최초 승인에서 확정할 카드사.
        @NotNull CardCompany cardCompany,

        // 카드 인증에만 사용하며 PG 이력/Outbox에는 저장하지 않는다.
        @NotBlank @Size(max = 40) String cardNumber,

        // 카드 인증값. 로그·응답·DB에 저장하지 않는다.
        @NotBlank @Size(max = 10) String cvc,

        // 카드 인증값. 로그·응답·DB에 저장하지 않는다.
        @NotBlank @Size(max = 20) String cardPassword,

        // 저장된 원거래와 비교할 금액.
        @NotNull @Positive BigDecimal amount
) {

    // record의 기본 toString으로 카드 인증값이 노출되지 않게 한다.
    @Override
    public String toString() {

        return "GatewayCardApprovalRequest[REDACTED]";
    }
}
