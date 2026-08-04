package dev.bum.admin_service.kafka.dlq;

public record DlqMessageHandleResponse(
        String result,
        String dltTopic,
        int partition,
        long offset,
        String targetTopic,
        String messageKey,
        String operator,
        String reason
) {
}
