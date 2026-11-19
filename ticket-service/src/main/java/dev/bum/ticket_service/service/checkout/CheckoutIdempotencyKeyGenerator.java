package dev.bum.ticket_service.service.checkout;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class CheckoutIdempotencyKeyGenerator {

    // 멱등성 키에 포함할 날짜 표시 형식.
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    // 날짜 계산에 사용할 서비스 시간대.
    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    /**
     * 구역 설정에 따라 좌석을 생성한다.
     */
    public String generate() {

        String date = LocalDate.now(SERVICE_ZONE_ID).format(DATE_FORMATTER);
        String randomValue = UUID.randomUUID().toString().replace("-", "");
        return "CHK-" + date + "-" + randomValue;
    }
}
