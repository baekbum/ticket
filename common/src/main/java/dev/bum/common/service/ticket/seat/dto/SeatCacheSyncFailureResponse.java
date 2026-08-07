package dev.bum.common.service.ticket.seat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class SeatCacheSyncFailureResponse {

    private Long id;
    private String operation;
    private String keyPrefix;
    private String redisKeys;
    private String targetValue;
    private String failureMessage;
    private String status;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastFailedAt;
    private LocalDateTime resolvedAt;
    private String resolvedMessage;
}
