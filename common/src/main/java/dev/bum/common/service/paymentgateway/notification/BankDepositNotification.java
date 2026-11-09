package dev.bum.common.service.paymentgateway.notification;

import dev.bum.common.service.ticket.payment.enums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankDepositNotification(
        // 공통 통보의 규격 버전.
        @Min(1) @Max(1) int version,

        // 최초 입금의 안정적인 이벤트 식별자.
        @NotBlank @Size(max = 100) String eventId,

        // 통보를 수신할 가맹점.
        @NotNull @Positive Long merchantId,

        // 은행 계좌가 연결된 PG 거래.
        @NotBlank @Size(max = 44) String pgPaymentId,

        // 가맹점 내부의 원결제 번호.
        @NotBlank @Size(max = 60) String merchantPaymentNo,

        // 가맹점에서 검증한 예매 소유자 참조값.
        @NotBlank @Size(max = 50) String buyerReference,

        // 역직렬화와 수신 처리 분기의 기준. 은행 입금만 허용한다.
        @NotNull PaymentMethod paymentMethod,

        // 발생 당시 금융 완료 사실. 통보 완료 상태나 실제 자금 반환 여부와 다르다.
        @NotNull PaymentStatus status,

        // 최초 입금 금액.
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,

        // 실제 입금 계좌.
        @NotBlank @Size(max = 30) String accountNumber,

        // 최초 입금 은행.
        @NotNull BankCompany bankCompany,

        // 최초 입금자명.
        @NotBlank @Size(max = 50) String depositorName,

        // 최초 입금 시각. 중복·지연 통보에도 바뀌지 않는다.
        @NotNull LocalDateTime depositedAt
) implements GatewayPaymentNotification {

    // 현재 은행 입금 DTO는 카드 승인과 함께 공통 수신 API의 버전 1 계약을 사용한다.
    public static final String CONTRACT = GatewayPaymentNotification.CONTRACT;

    // 외부 요청에서도 고정된 은행 입금 이벤트 종류와 원거래 연결을 검증한다.
    public void validateContract() {

        if (version != 1 || paymentMethod != PaymentMethod.BANK_TRANSFER || status != PaymentStatus.PAID
                || pgPaymentId == null || !(pgPaymentId + ":DEPOSIT:INITIAL").equals(eventId)) {
            throw new IllegalArgumentException("지원하지 않는 PG 은행 입금 통보 규격입니다.");
        }
    }
}
