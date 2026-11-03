package dev.bum.common.service.queue.dto;

import java.time.Instant;

public record QueueEnterResponse(
        // 공연 식별자.
        Long eventId,

        // 대기열 상태.
        String status,

        // 대기 순번.
        Long rank,

        // 현재 대기 인원 수.
        Long waitingCount,

        // 인증 또는 대기열 토큰.
        String token,

        // 만료까지 남은 시간(초).
        Long expiresInSeconds,

        // 예상 입장 시각.
        Instant estimatedEntryAt,

        // 입장 토큰 만료 시각.
        Instant activeTokenExpiresAt
) {
}
