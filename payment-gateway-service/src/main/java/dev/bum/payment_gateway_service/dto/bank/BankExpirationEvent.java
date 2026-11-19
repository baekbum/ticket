package dev.bum.payment_gateway_service.dto.bank;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record BankExpirationEvent(
        // 최초 계좌 만료 작업의 고정 식별자.
        @NotBlank @Size(max = 100) String eventId,

        // 은행 계좌에 저장된 PG 원거래 식별자.
        @NotBlank @Size(max = 44) String pgPaymentId,

        // 만료가 확정된 최초 계좌번호.
        @NotBlank @Size(max = 30) String accountNumber,

        // 최초 발급 은행.
        @NotNull BankCompany bankCompany,

        // 최초 발급 금액.
        @NotNull @Positive BigDecimal amount,

        // 은행이 입금을 허용한 마지막 시각.
        @NotNull LocalDateTime expiresAt,

        // 은행 잠금 아래 미입금을 확인하고 만료를 확정한 시각.
        @NotNull LocalDateTime expiredAt
) {

    // JSON 재전달과 DB 마이크로초 정밀도에서도 최초 이벤트를 유지한다.
    public BankExpirationEvent {

        if (amount != null) amount = amount.setScale(2, RoundingMode.UNNECESSARY);

        if (expiresAt != null) expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);

        if (expiredAt != null) expiredAt = expiredAt.truncatedTo(ChronoUnit.MICROS);
    }

    // 같은 거래를 임의의 새 만료 이벤트로 재등록하지 못하게 한다.
    public void validate() {

        if (pgPaymentId == null || pgPaymentId.isBlank() || pgPaymentId.length() > 44
                || !(pgPaymentId + ":EXPIRE:INITIAL").equals(eventId)
                || accountNumber == null || accountNumber.isBlank() || accountNumber.length() > 30
                || bankCompany == null || amount == null || amount.signum() <= 0
                || expiresAt == null || expiredAt == null || !expiredAt.isAfter(expiresAt)) {
            throw new IllegalArgumentException("은행 계좌 만료의 최초 금융 사실이 올바르지 않습니다.");
        }
    }
}
