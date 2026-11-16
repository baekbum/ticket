package dev.bum.payment_gateway_service.dto.card;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinancialCardApprovalGateRequest(
        // 금융 승인과 차단을 직렬화하는 원거래 키.
        @NotBlank String pgPaymentId,
        // 최초 구매자.
        @NotBlank String buyerReference,
        // 최초 금액.
        @NotNull @Positive BigDecimal amount,
        // 최초 카드 승인 기한. 은행 입금 기한으로 덮어쓰지 않는다.
        @NotNull LocalDateTime expiresAt
) {
    public FinancialCardApprovalGateRequest {

        if (amount != null) {
            try {
                amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
            } catch (ArithmeticException failure) {
                throw new IllegalArgumentException("카드 원거래 금액이 올바르지 않습니다.");
            }
        }

        if (expiresAt != null) {
            expiresAt = expiresAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
    }

    public void validate() {

        if (pgPaymentId == null || pgPaymentId.isBlank() || pgPaymentId.length() > 44
                || buyerReference == null || buyerReference.isBlank() || buyerReference.length() > 50
                || amount == null || amount.signum() <= 0 || expiresAt == null) {
            throw new IllegalArgumentException("카드 승인 차단의 원거래 조건이 올바르지 않습니다.");
        }
    }
}
