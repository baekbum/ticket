package dev.bum.payment_gateway_service.dto.card;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record FinancialCardApproveRequest(
        // 카드 승인과 연결할 PG 거래. 가맹점 결제번호는 전달하지 않는다.
        @NotBlank @Size(max = 44) String pgPaymentId,

        // PG가 원거래와 대조한 예매자 참조값.
        @NotBlank @Size(max = 50) String buyerReference,

        // 승인할 카드사.
        @NotNull CardCompany cardCompany,

        // 요청 처리 중에만 사용하는 카드번호.
        @NotBlank @Size(max = 40) String cardNumber,

        // 원문을 저장하거나 작업 해시에 포함하지 않는 인증값.
        @NotBlank @Size(max = 10) String cvc,

        // 원문을 저장하거나 작업 해시에 포함하지 않는 인증값.
        @NotBlank @Size(max = 20) String cardPassword,

        // 저장된 PG 거래의 승인 금액.
        @NotNull @Positive BigDecimal amount,

        // 신규 실행에만 적용한다. 기존 승인 재응답은 만료 후에도 허용한다.
        @NotNull LocalDateTime expiresAt
) {

    // 금융 입력의 금액·시각 정밀도를 DB와 맞춘다.
    public FinancialCardApproveRequest {

        if (amount == null || amount.signum() <= 0 || expiresAt == null) {
            throw new IllegalArgumentException("카드 승인 입력이 올바르지 않습니다.");
        }

        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("승인 금액은 소수 두 자리까지 허용합니다.");
        }

        if (amount.precision() > 15) {
            throw new IllegalArgumentException("승인 금액이 허용 범위를 초과했습니다.");
        }

        expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }

    // record 기본 문자열 표현에 카드 비밀정보가 포함되는 것을 막는다.
    @Override
    public String toString() {

        return "FinancialCardApproveRequest[REDACTED]";
    }
}
