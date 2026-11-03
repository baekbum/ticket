package dev.bum.common.service.ticket.seat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SeatCacheSyncFailureCondRequest {

    // 수행할 작업 종류.
    private String operation;

    // Redis 키 접두사.
    private String keyPrefix;

    // 좌석 캐시 동기화 실패 정보 상태.
    private String status;

    // 조회할 페이지.
    private Integer page;

    // 페이지당 조회 건수.
    private Integer size;
}
