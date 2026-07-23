package dev.bum.queue_service.dto;

public record QueueEnterResponse(
        Long eventId,
        String status,
        Long rank,
        Long waitingCount,
        String token,
        Long expiresInSeconds
) {
}
