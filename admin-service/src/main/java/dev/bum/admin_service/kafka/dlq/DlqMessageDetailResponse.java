package dev.bum.admin_service.kafka.dlq;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class DlqMessageDetailResponse {

    private final String dltTopic;
    private final int partition;
    private final long offset;
    private final String targetTopic;
    private final String messageKey;
    private final String payload;
    private final String payloadBase64;
    private final List<DlqHeaderResponse> headers;
    private final LocalDateTime occurredAt;
    private final String processingStatus;
    private final String handleAction;
    private final String handleStatus;
    private final String handledOperator;
    private final String handleReason;
    private final String handleErrorMessage;
    private final LocalDateTime handledAt;

    public DlqMessageDetailResponse(
            String dltTopic,
            int partition,
            long offset,
            String targetTopic,
            String messageKey,
            String payload,
            String payloadBase64,
            List<DlqHeaderResponse> headers,
            LocalDateTime occurredAt,
            String processingStatus,
            String handleAction,
            String handleStatus,
            String handledOperator,
            String handleReason,
            String handleErrorMessage,
            LocalDateTime handledAt
    ) {
        this.dltTopic = dltTopic;
        this.partition = partition;
        this.offset = offset;
        this.targetTopic = targetTopic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.payloadBase64 = payloadBase64;
        this.headers = headers;
        this.occurredAt = occurredAt;
        this.processingStatus = processingStatus;
        this.handleAction = handleAction;
        this.handleStatus = handleStatus;
        this.handledOperator = handledOperator;
        this.handleReason = handleReason;
        this.handleErrorMessage = handleErrorMessage;
        this.handledAt = handledAt;
    }
}
