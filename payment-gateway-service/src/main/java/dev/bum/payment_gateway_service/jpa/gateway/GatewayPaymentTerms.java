package dev.bum.payment_gateway_service.jpa.gateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

public record GatewayPaymentTerms(
        // 가맹점 안에서 유일한 최초 결제번호.
        String paymentNo,

        // 서버 인증을 거친 가맹점이 확인한 예매자.
        String buyerReference,

        // 최초 요청 수단. 명시적 수단 전환과 구분해 해시를 보존한다.
        PaymentMethod paymentMethod,

        // 두 자리 소수로 정규화한 불변 금액.
        BigDecimal amount,

        // 최초 승인 또는 입금 기한.
        LocalDateTime expiresAt,

        // 계좌 결제의 은행. 카드 준비에는 사용하지 않는다.
        BankCompany bankCompany
) {

    // 직접 서비스 호출도 HTTP DTO와 같은 제약을 적용한다.
    public GatewayPaymentTerms {

        requireText(paymentNo, 60);
        requireText(buyerReference, 50);

        if (paymentMethod != PaymentMethod.CREDIT_CARD && paymentMethod != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("지원하지 않는 PG 결제수단입니다.");
        }

        if (amount == null || amount.signum() <= 0 || expiresAt == null) {
            throw new IllegalArgumentException("양수 금액과 결제 기한이 필요합니다.");
        }

        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("결제 금액은 소수 두 자리까지 허용합니다.");
        }

        if (amount.precision() > 15) {
            throw new IllegalArgumentException("결제 금액이 허용 범위를 초과했습니다.");
        }

        if ((paymentMethod == PaymentMethod.BANK_TRANSFER && bankCompany == null)
                || (paymentMethod == PaymentMethod.CREDIT_CARD && bankCompany != null)) {
            throw new IllegalArgumentException("결제수단과 은행 정보가 일치하지 않습니다.");
        }

        expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }

    // 필드 길이를 포함해 구분 문자 충돌 없이 최초 요청 해시를 계산한다.
    public String fingerprint() {

        StringBuilder canonical = new StringBuilder();

        for (String value : new String[]{paymentNo, buyerReference, paymentMethod.name(), amount.toPlainString(),
                expiresAt.toString(), bankCompany == null ? "" : bankCompany.name()}) {
            canonical.append(value.length()).append(':').append(value);
        }

        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("PG 요청 해시를 생성할 수 없습니다.", exception);
        }
    }

    // 식별 문자열의 암묵적인 공백 변환과 잘림을 방지한다.
    private static void requireText(String value, int maxLength) {

        if (value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.strip())) {
            throw new IllegalArgumentException("PG 결제번호 또는 예매자 정보가 올바르지 않습니다.");
        }
    }
}
