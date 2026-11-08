package dev.bum.payment_gateway_service.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public final class GatewayPaymentIdNormalizer {

    private GatewayPaymentIdNormalizer() {

    }

    // 날짜 접두사와 UUID를 검증하고 기존 36자 UUID 조회도 유지한다.
    public static String normalize(String paymentId) {

        if (paymentId == null || (paymentId.length() != 36 && paymentId.length() != 44)) {
            throw new IllegalArgumentException("PG 결제 식별자 형식이 올바르지 않습니다.");
        }

        if (paymentId.length() == 36) {
            return UUID.fromString(paymentId).toString();
        }

        String issuedDate = paymentId.substring(0, 8);

        try {
            LocalDate.parse(issuedDate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("PG 결제 식별자의 발급일이 올바르지 않습니다.", exception);
        }

        return issuedDate + UUID.fromString(paymentId.substring(8)).toString();
    }
}
