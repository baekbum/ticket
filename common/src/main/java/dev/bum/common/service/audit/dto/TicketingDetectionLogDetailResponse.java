package dev.bum.common.service.audit.dto;

import dev.bum.common.kafka.ticketing.TicketingDetectionType;

import java.time.Instant;
import java.util.Map;

public record TicketingDetectionLogDetailResponse(
        Long id,
        Long eventId,
        String userId,
        TicketingDetectionType detectionType,
        Instant occurredAt,
        String traceId,
        String sessionKeyHash,
        Map<String, Object> evidence
) {
}
