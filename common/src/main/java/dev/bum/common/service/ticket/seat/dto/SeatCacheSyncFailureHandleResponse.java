package dev.bum.common.service.ticket.seat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SeatCacheSyncFailureHandleResponse {

    private Long id;
    private String status;
    private int retryCount;
    private String message;

    public SeatCacheSyncFailureHandleResponse(Long id, String status, int retryCount, String message) {
        this.id = id;
        this.status = status;
        this.retryCount = retryCount;
        this.message = message;
    }
}
