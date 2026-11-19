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

    // 조회 또는 처리 모드.
    private QueueRedisInspectMode mode;

    // 공연 식별자.
    private Long eventId;

    // 인증 또는 대기열 토큰.
    private String token;

    // 최대 조회 건수.
    private Integer limit;

    // 조회 또는 처리 건수.
    private Integer count;

    // 조회한 항목 목록.
    private List<QueueRedisEntryResponse> entries;
}
