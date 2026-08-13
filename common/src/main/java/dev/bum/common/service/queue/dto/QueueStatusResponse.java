package dev.bum.common.service.queue.dto;

public record QueueStatusResponse(
        // 대기열을 적용하는 공연/이벤트 ID
        Long eventId,

        // 현재 대기열 상태. 입장 가능하면 READY, 대기 중이면 WAITING
        String status,

        // 현재 사용자의 대기 순번. WAITING이면 1부터 시작하는 순번, READY이면 0
        Long rank,

        // 해당 이벤트의 전체 대기 인원 수
        Long waitingCount,

        // WAITING이면 대기 유지 토큰, READY이면 입장 가능 토큰
        String token,

        // 발급된 토큰의 남은 유효 시간(초)
        Long expiresInSeconds
) {
    public QueueEnterResponse toEnterResponse() {
        return new QueueEnterResponse(eventId, status, rank, waitingCount, token, expiresInSeconds);
    }
}
