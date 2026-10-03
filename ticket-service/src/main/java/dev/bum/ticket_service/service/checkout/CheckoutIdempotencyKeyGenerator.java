package dev.bum.ticket_service.service.checkout;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class CheckoutIdempotencyKeyGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    public String generate() {
        String date = LocalDate.now(SERVICE_ZONE_ID).format(DATE_FORMATTER);
        String randomValue = UUID.randomUUID().toString().replace("-", "");
        return "CHK-" + date + "-" + randomValue;
    }
}
