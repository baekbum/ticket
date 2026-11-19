package dev.bum.payment_gateway_service.dto.bank;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record BankVirtualAccountIssueRequest(
        // 은행 업무와 연결할 PG 거래 식별자. 가맹점 결제번호는 은행으로 전달하지 않는다.
        @NotBlank @Size(max = 44) String pgPaymentId,

        // 발급할 은행.
        @NotNull BankCompany bankCompany,

        // PG가 원거래와 대조한 금액.
        @NotNull @Positive BigDecimal amount,

        // 공연일과 무관하게 PG가 전달하는 일반적인 입금 기한.
        @NotNull LocalDateTime expiresAt
) {

    // HTTP 외의 직접 호출도 같은 값으로 비교할 수 있도록 금액과 기한을 정규화한다.
    public BankVirtualAccountIssueRequest {

        if (pgPaymentId == null || pgPaymentId.isBlank() || pgPaymentId.length() > 44
                || bankCompany == null || amount == null || amount.signum() <= 0 || expiresAt == null) {
            throw new IllegalArgumentException("은행 발급 요청 정보가 올바르지 않습니다.");
        }

        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("발급 금액은 소수 두 자리까지 허용합니다.");
        }

        if (amount.precision() > 15) {
            throw new IllegalArgumentException("발급 금액이 허용 범위를 초과했습니다.");
        }

        expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }
}
