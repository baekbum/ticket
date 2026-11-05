package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPaymentTerms;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayPaymentPrepareRequest(
        // 선택적 교차 검증 값. 이 값으로 가맹점을 인증하거나 선택하지 않는다.
        @Positive Long merchantId,

        // 가맹점 내부 결제번호.
        @NotBlank @Size(max = 60) String paymentNo,

        // 가맹점이 확인한 예매자.
        @NotBlank @Size(max = 50) String buyerReference,

        // 신규 준비의 결제수단.
        @NotNull PaymentMethod paymentMethod,

        // 불변 요청 값에서 유효 자릿수를 검증한다. 의미 없는 소수점 0은 허용한다.
        @NotNull @Positive BigDecimal amount,

        // 가맹점이 결정한 일반적인 결제 기한.
        @NotNull LocalDateTime expiresAt,

        // 계좌 결제에 필요한 은행.
        BankCompany bankCompany
) {

    // HTTP와 내부 호출에서 공통으로 사용하는 불변 요청 값으로 변환한다.
    public GatewayPaymentTerms toTerms() {

        return new GatewayPaymentTerms(paymentNo, buyerReference, paymentMethod, amount, expiresAt, bankCompany);
    }
}
