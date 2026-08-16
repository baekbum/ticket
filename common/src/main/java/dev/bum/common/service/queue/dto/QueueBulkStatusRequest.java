package dev.bum.common.service.queue.dto;

import java.util.List;
import java.util.Map;

public record QueueBulkStatusRequest(
        List<String> userIds,
        Map<String, String> tokenByUserId
) {
}
