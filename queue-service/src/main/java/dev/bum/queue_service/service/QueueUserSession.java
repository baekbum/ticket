package dev.bum.queue_service.service;

public record QueueUserSession(
        QueueSessionType type,
        String token,
        long expiresInSeconds
) {
}
