package dev.bum.common.service.queue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QueueCompleteRequest(
        @NotNull Long eventId,
        @NotBlank String userId,
        @NotBlank String token
) {
}
