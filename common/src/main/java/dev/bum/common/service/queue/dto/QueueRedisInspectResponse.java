package dev.bum.common.service.queue.dto;

import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueRedisInspectResponse {
    private QueueRedisInspectMode mode;
    private Long eventId;
    private String token;
    private Integer limit;
    private Integer count;
    private List<QueueRedisEntryResponse> entries;
}
