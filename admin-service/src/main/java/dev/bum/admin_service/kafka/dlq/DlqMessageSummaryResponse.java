package dev.bum.admin_service.kafka.dlq;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class DlqMessageSummaryResponse {

    private final String dltTopic;
    private final int partition;
    private final long offset;
    private final String messageKey;
    private final String payloadPreview;
    private final List<DlqHeaderResponse> headers;
    private final LocalDateTime occurredAt;
    private final String processingStatus;

    public DlqMessageSummaryResponse(
            String dltTopic,
            int partition,
            long offset,
            String messageKey,
            String payloadPreview,
            List<DlqHeaderResponse> headers,
            LocalDateTime occurredAt,
            String processingStatus
    ) {
        this.dltTopic = dltTopic;
        this.partition = partition;
        this.offset = offset;
        this.messageKey = messageKey;
        this.payloadPreview = payloadPreview;
        this.headers = headers;
        this.occurredAt = occurredAt;
        this.processingStatus = processingStatus;
    }
}
