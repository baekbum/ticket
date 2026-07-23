package dev.bum.common.service.queue.dto;

public record QueueEnterResponse(
        Long eventId,
        String status,
        Long rank,
        Long waitingCount,
        String token,
        Long expiresInSeconds
) {
}
