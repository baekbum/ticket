package dev.bum.queue_service.service;

public record QueueUserSession(
        // 데이터 또는 작업 유형.
        QueueSessionType type,

        // 인증 또는 대기열 토큰.
        String token,

        // 만료까지 남은 시간(초).
        long expiresInSeconds
) {
}
