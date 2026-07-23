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
    private String key;
    private String member;
    private Double score;
    private Long rank;
    private Long timestampMillis;
    private String value;
    private Long ttlSeconds;
    private String token;
}
