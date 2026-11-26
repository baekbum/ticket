package dev.bum.common.service.queue.dto;

import java.time.Instant;

public record QueueValidateResponse(
        // 요청 허용 여부.
        boolean allowed,

        // 처리 사유.
        String reason,
        Instant activeTokenExpiresAt,
        Instant activeTokenIssuedAt
) {
    public QueueValidateResponse(boolean allowed, String reason) {
        this(allowed, reason, null, null);
    }

    public QueueValidateResponse(boolean allowed, String reason, Instant activeTokenExpiresAt) {
        this(allowed, reason, activeTokenExpiresAt, null);
    }
}
