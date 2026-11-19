package dev.bum.common.service.ticket.seat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SeatCacheSyncFailureHandleResponse {

    // 고유 식별자.
    private Long id;

    // 좌석 캐시 동기화 실패 정보 상태.
    private String status;

    // 재시도 횟수.
    private int retryCount;

    // 처리 결과 메시지.
    private String message;

    // 전달받은 값으로 좌석 캐시 동기화 실패 정보 객체를 초기화한다.
    public SeatCacheSyncFailureHandleResponse(Long id, String status, int retryCount, String message) {

        this.id = id;
        this.status = status;
        this.retryCount = retryCount;
        this.message = message;
    }
}
