package dev.bum.queue_service.dto;

public record QueueValidateResponse(
        boolean allowed,
        String reason
) {
}
