package dev.bum.common.service.audit.dto;

import dev.bum.common.kafka.ticketing.TicketingDetectionType;

import java.time.Instant;

public record TicketingDetectionLogListResponse(
        Long id,
        Long eventId,
        String userId,
        TicketingDetectionType detectionType,
        Instant occurredAt
) {
}
