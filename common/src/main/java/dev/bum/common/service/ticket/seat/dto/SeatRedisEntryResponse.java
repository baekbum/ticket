package dev.bum.common.service.ticket.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatRedisEntryResponse {

    // 데이터 식별 키.
    private String key;

    // 데이터 값.
    private String value;

    // 유효 시간(초).
    private Long ttlSeconds;

    // 데이터 또는 작업 유형.
    private String type;

    // 좌석 상태.
    private String status;

    // 잠금 여부.
    private Boolean locked;

    // 잠금에 사용하는 Redis 키.
    private String lockKey;

    // 잠금 소유자를 식별하는 값.
    private String lockValue;

    // 잠금 유효 시간(초).
    private Long lockTtlSeconds;
}
