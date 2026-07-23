package dev.bum.queue_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QueueValidateRequest(
        @NotNull Long eventId,
        @NotBlank String userId,
        @NotBlank String token
) {
}
