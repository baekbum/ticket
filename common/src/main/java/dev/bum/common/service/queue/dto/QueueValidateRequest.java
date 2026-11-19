package dev.bum.common.service.queue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QueueValidateRequest(
        // 공연 식별자.
        @NotNull Long eventId,

        // 사용자 ID.
        @NotBlank String userId,

        // 인증 또는 대기열 토큰.
        @NotBlank String token
) {
}
