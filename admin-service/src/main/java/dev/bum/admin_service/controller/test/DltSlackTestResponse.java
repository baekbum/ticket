package dev.bum.admin_service.controller.test;

import java.time.OffsetDateTime;

public record DltSlackTestResponse(
        boolean sent,
        String skippedReason,
        String originTopic,
        String dltTopic,
        String adminDlqUrl,
        OffsetDateTime requestedAt
) {
}
