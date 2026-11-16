package dev.bum.common.service.paymentgateway.refund;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public record CardRefundCommand(
        // 카드 원승인의 PG 연결 키.
        @NotBlank @Size(max = 44) String pgPaymentId,

        // 재시도와 철회에서 유지하는 개별 환불 신청 키.
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,59}") String refundRequestId,

        // 카드 원승인에서 돌려줄 최초 확정 금액.
        @NotNull @Positive BigDecimal amount
) {

    public CardRefundCommand {

        if (amount != null) {
            try {
                amount = amount.setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException failure) {
                throw new IllegalArgumentException("카드 환불 금액은 소수 둘째 자리까지만 가능합니다.");
            }
        }
    }

    public void validate() {

        if (pgPaymentId == null || pgPaymentId.isBlank() || pgPaymentId.length() > 44
                || refundRequestId == null || !refundRequestId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,59}")
                || amount == null || amount.signum() <= 0 || amount.precision() > 15) {
            throw new IllegalArgumentException("카드 환불 요청 정보가 올바르지 않습니다.");
        }
    }

    // 실행과 철회는 같은 신청이다. 카드 환불 규격·원거래·신청 키·금액을 함께 고정한다.
    public String fingerprint() {

        validate();

        try {
            String value = String.join("\n", "CARD_REFUND_V1", pgPaymentId, refundRequestId, amount.toPlainString());

            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("카드 환불 요청 해시를 사용할 수 없습니다.", failure);
        }
    }
}
