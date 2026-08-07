package dev.bum.admin_service.kafka.dlq;

import lombok.Getter;

@Getter
public class DlqMessageHandleResponse {

    private final String result;
    private final String dltTopic;
    private final int partition;
    private final long offset;
    private final String targetTopic;
    private final String messageKey;
    private final String operator;
    private final String reason;

    public DlqMessageHandleResponse(
            String result,
            String dltTopic,
            int partition,
            long offset,
            String targetTopic,
            String messageKey,
            String operator,
            String reason
    ) {
        this.result = result;
        this.dltTopic = dltTopic;
        this.partition = partition;
        this.offset = offset;
        this.targetTopic = targetTopic;
        this.messageKey = messageKey;
        this.operator = operator;
        this.reason = reason;
    }
}
