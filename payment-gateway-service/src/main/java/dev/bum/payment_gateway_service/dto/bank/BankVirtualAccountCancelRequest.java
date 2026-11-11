package dev.bum.payment_gateway_service.dto.bank;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record BankVirtualAccountCancelRequest(
        // 은행 계좌에 불변으로 연결된 PG 원거래.
        @NotBlank @Size(max = 44) String pgPaymentId,

        // PG가 원거래와 대조한 은행.
        @NotNull BankCompany bankCompany,

        // 취소할 계좌 번호.
        @NotBlank @Size(max = 30) String accountNumber,

        // 원거래의 전체 금액.
        @NotNull @Positive BigDecimal amount
) {

    // 직접 호출과 HTTP 호출 모두 같은 원거래 금액으로 비교한다.
    public BankVirtualAccountCancelRequest {

        if (pgPaymentId == null || pgPaymentId.isBlank() || pgPaymentId.length() > 44
                || bankCompany == null || accountNumber == null || accountNumber.isBlank() || accountNumber.length() > 30
                || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("은행 계좌 취소 요청이 올바르지 않습니다.");
        }

        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("취소 금액은 소수 두 자리까지 허용합니다.");
        }

        if (amount.precision() > 15) {
            throw new IllegalArgumentException("취소 금액이 허용 범위를 초과했습니다.");
        }
    }
}
