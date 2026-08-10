package dev.bum.admin_service.controller.test;

import java.time.OffsetDateTime;

public record DltTestPublishResponse(
        String dltTopic,
        Integer partition,
        Long offset,
        String key,
        OffsetDateTime publishedAt
) {
}
