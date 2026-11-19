package dev.bum.common.service.paymentgateway.refund;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record BankRefundCommand(
        // 금융 원거래 연결 키.
        @NotBlank @Size(max = 44) String pgPaymentId,

        // 재시도·철회에서 바꾸지 않는 가맹점 환불 요청 키.
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,59}") String refundRequestId,

        // 최초 확정 환불 금액.
        @NotNull @Positive BigDecimal amount,

        // 환불받을 은행.
        @NotNull BankCompany refundBankCompany,

        // 전송 중에만 사용하는 환불 계좌 원문.
        @NotBlank @Size(max = 50) String refundAccountNumber,

        // 최초 확정 예금주.
        @NotBlank @Size(max = 50) String refundAccountHolder
) {

    // 같은 금융 요청의 금액 표현과 계좌 앞뒤 공백을 정규화한다.
    public BankRefundCommand {

        if (amount != null) amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);

        if (refundAccountNumber != null) refundAccountNumber = refundAccountNumber.strip();
        if (refundAccountHolder != null) refundAccountHolder = refundAccountHolder.strip();
    }

    // 메서드 직접 호출에서도 잘못된 금융 입력을 거절한다.
    public void validate() {

        if (pgPaymentId == null || pgPaymentId.isBlank() || pgPaymentId.length() > 44
                || refundRequestId == null || !refundRequestId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,59}")
                || amount == null || amount.signum() <= 0 || amount.precision() > 15 || refundBankCompany == null
                || refundAccountNumber == null || refundAccountNumber.isBlank() || refundAccountNumber.length() > 50
                || refundAccountHolder == null || refundAccountHolder.isBlank() || refundAccountHolder.length() > 50) {
            throw new IllegalArgumentException("환불 요청 정보가 올바르지 않습니다.");
        }
    }

    // 원문 계좌 대신 동일 요청 검증용 해시만 기록한다.
    public String fingerprint() {

        validate();

        try {
            String value = String.join("\n", pgPaymentId, refundRequestId, amount.toPlainString(),
                    refundBankCompany.name(), refundAccountNumber, refundAccountHolder);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("환불 요청 해시를 사용할 수 없습니다.", exception);
        }
    }

    // 계좌·예금주 원문이 로그에 출력되지 않도록 한다.
    @Override
    public String toString() {

        return "BankRefundCommand[pgPaymentId=" + pgPaymentId + ", refundRequestId=" + refundRequestId + "]";
    }
}
