package dev.bum.common.service.queue.dto;

import java.util.List;

public record QueueBulkStatusRequest(
        List<String> userIds
) {
}
