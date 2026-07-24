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
    private String key;
    private String value;
    private Long ttlSeconds;
    private String type;
    private String status;
    private Boolean locked;
    private String lockKey;
    private String lockValue;
    private Long lockTtlSeconds;
}
