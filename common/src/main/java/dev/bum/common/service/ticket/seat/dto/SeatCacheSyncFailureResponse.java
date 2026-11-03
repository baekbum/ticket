package dev.bum.common.service.ticket.seat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class SeatCacheSyncFailureResponse {

    // 고유 식별자.
    private Long id;

    // 수행할 작업 종류.
    private String operation;

    // Redis 키 접두사.
    private String keyPrefix;

    // 조회하거나 처리할 Redis 키 목록.
    private String redisKeys;

    // 처리 대상 값.
    private String targetValue;

    // 실패 상세 메시지.
    private String failureMessage;

    // 좌석 캐시 동기화 실패 정보 상태.
    private String status;

    // 재시도 횟수.
    private int retryCount;

    // 생성 시각.
    private LocalDateTime createdAt;

    // 마지막 실패 시각.
    private LocalDateTime lastFailedAt;

    // 문제 해결 시각.
    private LocalDateTime resolvedAt;

    // 문제 해결 내용.
    private String resolvedMessage;
}
