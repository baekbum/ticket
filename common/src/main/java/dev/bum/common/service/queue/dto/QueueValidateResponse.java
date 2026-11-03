package dev.bum.common.service.queue.dto;

public record QueueValidateResponse(
        // 요청 허용 여부.
        boolean allowed,

        // 처리 사유.
        String reason
) {
}
