package dev.bum.common.service.queue.dto;

import java.time.Instant;

public record QueueEnterResponse(
        Long eventId,
        String status,
        Long rank,
        Long waitingCount,
        String token,
        Long expiresInSeconds,
        Instant estimatedEntryAt,
        Instant activeTokenExpiresAt
) {
}
