package dev.bum.common.service.queue.dto;

public record QueueValidateResponse(
        boolean allowed,
        String reason
) {
}
