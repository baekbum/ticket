package dev.bum.ticket_service.controller.test;

import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import dev.bum.ticket_service.kafka.ticketing.TicketingDetectionLogProducer;
import dev.bum.ticket_service.service.queue.BookingSessionKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Profile("local")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manage/test/ticketing-detection-log")
public class TicketingDetectionLogTestController {

    private static final long TEST_EVENT_ID = 0L;

    private final TicketingDetectionLogProducer producer;

    @PostMapping("/publish")
    public ResponseEntity<TestRun> publishSamples(@Valid @RequestBody PublishRequest request) {
        String userId = request.userId().strip();
        String sessionNonce = UUID.randomUUID().toString();
        String sessionKeyHash = BookingSessionKey.hash(TEST_EVENT_ID, userId, sessionNonce);
        Instant occurredAt = Instant.now();
        List<TicketingDetectionType> types = request.detectionType() == null
                ? List.of(TicketingDetectionType.values()) : List.of(request.detectionType());

        for (TicketingDetectionType type : types) {
            producer.report(TEST_EVENT_ID, userId, sessionNonce, type, occurredAt, sampleEvidence(type));
        }

        return ResponseEntity.accepted().body(new TestRun(userId, sessionKeyHash, types));
    }

    private Map<String, Object> sampleEvidence(TicketingDetectionType type) {
        return switch (type) {
            case CAPTCHA_ATTEMPT_LIMIT_REACHED -> Map.of("test", true, "attempts", 5);
            case AREA_LAYOUT_REFRESH_LIMIT_REACHED -> Map.of("test", true, "refreshCount", 60, "windowMs", 60_000);
            case PREPARE_TOO_FAST -> Map.of("test", true, "elapsedMs", 4_000, "limitMs", 5_000);
            case AREA_LAYOUT_ACTIVE_TOKEN_MISSING, SEAT_OCCUPATION_CAPTCHA_NOT_PASSED -> Map.of("test", true);
        };
    }

    public record PublishRequest(@NotBlank @Size(max = 50) String userId, TicketingDetectionType detectionType) {
    }

    public record TestRun(String userId, String sessionKeyHash, List<TicketingDetectionType> types) {
    }
}
