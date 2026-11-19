package dev.bum.common.service.queue.dto;

import java.util.List;
import java.util.Map;

public record QueueBulkStatusRequest(
        // 사용자 ID 목록.
        List<String> userIds,

        // 사용자 ID별 토큰 정보.
        Map<String, String> tokenByUserId
) {
}
