package dev.bum.audit_service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.audit.AuditLogEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogFingerprintTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AuditLogFingerprint fingerprint = new AuditLogFingerprint(mapper);

    @Test
    void nestedObjectKeyOrderAndNumericRepresentationDoNotChangeHash() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("title", "공연");
        first.put("nested", Map.of("a", 1, "b", List.of("x", "y")));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("nested", Map.of("b", List.of("x", "y"), "a", new BigDecimal("1.00")));
        second.put("title", "공연");

        assertThat(fingerprint.calculate(event(first))).isEqualTo(fingerprint.calculate(event(second)));
    }

    @Test
    void jsonRoundTripKeepsTheFingerprint() throws Exception {
        AuditLogEvent original = event(Map.of("seats", List.of(1, 2), "amount", 1234567890123L));
        AuditLogEvent replay = mapper.readValue(mapper.writeValueAsBytes(original), AuditLogEvent.class);

        assertThat(replay.getIdempotencyKey()).isEqualTo(original.getIdempotencyKey());
        assertThat(fingerprint.calculate(replay)).isEqualTo(fingerprint.calculate(original));
    }

    @Test
    void arrayOrderIsPartOfTheRequest() {
        assertThat(fingerprint.calculate(event(Map.of("seats", List.of(1, 2)))))
                .isNotEqualTo(fingerprint.calculate(event(Map.of("seats", List.of(2, 1)))));
    }

    @Test
    void nullAndEmptyObjectAreDifferent() {
        assertThat(fingerprint.calculate(event(null)))
                .isNotEqualTo(fingerprint.calculate(event(Map.of())));
    }

    private AuditLogEvent event(Map<String, Object> metadata) {
        return AuditLogEvent.builder()
                .idempotencyKey("key-1").serviceName("ticket-service")
                .occurredAt(LocalDateTime.of(2026, 10, 25, 12, 0, 0, 123456789))
                .actorType("ADMIN").actorId("admin").action("EVENT_UPDATE").result("SUCCESS")
                .metadata(metadata).build();
    }
}
