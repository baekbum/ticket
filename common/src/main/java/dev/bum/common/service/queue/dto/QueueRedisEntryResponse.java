package dev.bum.common.service.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueRedisEntryResponse {

    // 데이터 식별 키.
    private String key;

    // Redis 정렬 집합에 저장한 항목.
    private String member;

    // Redis 정렬 집합의 정렬 기준값.
    private Double score;

    // 대기 순번.
    private Long rank;

    // 기준 시각(밀리초).
    private Long timestampMillis;

    // 데이터 값.
    private String value;

    // 유효 시간(초).
    private Long ttlSeconds;

    // 인증 또는 대기열 토큰.
    private String token;
}
