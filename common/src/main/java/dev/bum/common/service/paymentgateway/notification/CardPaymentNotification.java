package dev.bum.common.service.paymentgateway.notification;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardPaymentNotification(
        // PG와 모든 가맹점이 공유하는 통보 규격 버전.
        @Min(1) @Max(1) int version,

        // 재시도에도 변하지 않는 승인 이벤트 ID.
        @NotBlank @Size(max = 100) String eventId,

        // PG가 등록한 대상 가맹점 코드.
        @NotNull @Positive Long merchantId,

        // 금융 원거래와 연결된 PG 결제 식별자.
        @NotBlank @Size(max = 44) String pgPaymentId,

        // 해당 가맹점 내부의 결제번호.
        @NotBlank @Size(max = 60) String merchantPaymentNo,

        // 가맹점에서 확인한 예매자 참조값.
        @NotBlank @Size(max = 50) String buyerReference,

        // 이번 통보는 카드 승인 결과다.
        @NotNull PaymentMethod paymentMethod,

        // 발생 당시 PG 금융 상태. 통보 전달 상태와 다르다.
        @NotNull PaymentStatus status,

        // 원승인 금액.
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,

        // 카드사의 원승인 식별자.
        @NotBlank @Size(max = 80) String transactionId,

        // 승인 카드사.
        @NotNull CardCompany cardCompany,

        // 원문 카드번호 대신 전달하는 마스킹 정보.
        @NotBlank @Size(max = 30) String maskedCardNumber,

        // PG가 보존한 최초 금융 승인 시각.
        @NotNull LocalDateTime approvedAt
) {

    // 통보 DTO의 필드 구성·타입·의미와 응답 처리 규칙을 묶은 계약 버전 식별자다.
    // 향후 V2가 다른 필드나 응답 형식을 사용한다면 V1과 구분하는 코드가 된다.
    // 상수를 선언하는 것만으로 DTO나 클래스를 자동 선택하지 않는다. 별도 선택/변환 로직이 필요하다.
    // 현재는 V1만 지원하며, 이 값은 지원 규격 검사와 Outbox 기록에 사용한다.
    // 실제 전송 DTO는 CardNotificationSnapshot.toNotification()에서 이 타입으로 고정 생성한다.
    public static final String CONTRACT = "CARD_NOTIFICATION_V1";

    // 호출 경로와 무관하게 승인 통보의 종류·버전·이벤트 연결을 검증한다.
    public void validateContract() {

        if (version != 1 || paymentMethod != PaymentMethod.CREDIT_CARD || status != PaymentStatus.PAID
                || pgPaymentId == null || !((pgPaymentId + ":APPROVE:INITIAL").equals(eventId))) {
            throw new IllegalArgumentException("지원하지 않는 PG 카드 통보 규격입니다.");
        }
    }
}
