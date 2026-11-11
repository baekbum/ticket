package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record GatewayVirtualAccountPaymentCancelRequest(
        // 인증 주체와 교차 검증할 선택적 코드. 이 값으로 가맹점을 선택하지 않는다.
        @Positive Long merchantId,

        // 가맹점이 확인한 원예매 소유자.
        @NotBlank @Size(max = 50) String buyerReference,

        // 원거래의 전체 금액. 부분 환불 요청이 아니다.
        @NotNull @Positive BigDecimal amount,

        // 저장된 원거래와 대조할 은행.
        @NotNull BankCompany bankCompany,

        // 최초 발급 계좌와 대조할 번호.
        @NotBlank @Size(max = 30) String accountNumber
) {
}
