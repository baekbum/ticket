package dev.bum.common.service.audit.dto;

import dev.bum.common.kafka.ticketing.TicketingDetectionType;

import java.time.Instant;

public record TicketingDetectionLogCondRequest(
        Long eventId,
        String userId,
        String sessionKeyHash,
        TicketingDetectionType detectionType,
        Instant occurredFrom,
        Instant occurredTo,
        Integer page,
        Integer size
) {
}
