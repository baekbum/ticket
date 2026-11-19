package dev.bum.common.service.paymentgateway.notification;

import dev.bum.common.service.ticket.payment.enums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankCancellationNotification(
        // 취소 결과 계약의 버전.
        @Min(1) @Max(1) int version,

        // 같은 전체 취소 작업의 고정 이벤트 식별자.
        @NotBlank @Size(max = 100) String eventId,

        // 수신 가맹점의 PG 코드.
        @NotNull @Positive Long merchantId,

        // 가맹점 번호와 구분한 PG 원거래.
        @NotBlank @Size(max = 44) String pgPaymentId,

        // 해당 가맹점의 결제번호.
        @NotBlank @Size(max = 60) String merchantPaymentNo,

        // 최초 검증된 예매 소유자.
        @NotBlank @Size(max = 50) String buyerReference,

        // 이 결과는 은행 계좌 취소에만 사용한다.
        @NotNull PaymentMethod paymentMethod,

        // 최초 취소 요청 금액. 실제 반환 금액이 아니다.
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,

        // 은행이 처리한 원계좌.
        @NotBlank @Size(max = 30) String accountNumber,

        // 원거래 은행.
        @NotNull BankCompany bankCompany,

        // CANCELLED 또는 REJECTED. 전달 성공과 업무 성공을 구분한다.
        @NotBlank String outcome,

        // 은행이 확정한 사유 코드.
        @NotBlank String reason,

        // PG 취소 확정 시각. 거절 결과에서는 null이다.
        LocalDateTime cancelledAt,

        // 계좌 취소는 실제 자금 반환을 수행하지 않는다.
        boolean fundsReturned
) {

    // 기존 등록 통보 URL에서 입금/카드 통보와 구분할 표준 헤더 값.
    public static final String EVENT_TYPE = "BANK_CANCELLATION";

    // 같은 금액의 100/100.0/100.00 표현을 동일한 멱등 내용으로 비교한다.
    public BankCancellationNotification {

        if (amount != null) {
            amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        }
    }

    // 가맹점이 임의의 작업·금액 반환을 승인된 결과로 받아들이지 않게 한다.
    public void validateContract() {

        if (version != 1 || paymentMethod != PaymentMethod.BANK_TRANSFER || fundsReturned
                || !(pgPaymentId + ":CANCEL:INITIAL").equals(eventId)
                || (!"CANCELLED".equals(outcome) && !"REJECTED".equals(outcome))
                || ("CANCELLED".equals(outcome) != (cancelledAt != null))) {
            throw new IllegalArgumentException("지원하지 않는 PG 은행 취소 통보 규격입니다.");
        }
    }
}
