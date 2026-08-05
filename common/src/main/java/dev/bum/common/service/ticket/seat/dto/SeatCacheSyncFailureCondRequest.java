package dev.bum.common.service.ticket.seat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SeatCacheSyncFailureCondRequest {

    private String operation;
    private String keyPrefix;
    private String status;
    private Integer page;
    private Integer size;
}
