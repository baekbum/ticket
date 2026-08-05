package dev.bum.admin_service.kafka.dlq;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class DlqMessageHandleHistoryResponse {

    private final Long id;
    private final String dltTopic;
    private final int partitionNo;
    private final long messageOffset;
    private final String messageKey;
    private final String targetTopic;
    private final String action;
    private final String status;
    private final String operator;
    private final String reason;
    private final String errorMessage;
    private final boolean payloadModified;
    private final String originalPayload;
    private final String modifiedPayload;
    private final LocalDateTime handledAt;

    public DlqMessageHandleHistoryResponse(DlqMessageHandleHistory history) {
        this.id = history.getId();
        this.dltTopic = history.getDltTopic();
        this.partitionNo = history.getPartitionNo();
        this.messageOffset = history.getMessageOffset();
        this.messageKey = history.getMessageKey();
        this.targetTopic = history.getTargetTopic();
        this.action = history.getAction().name();
        this.status = history.getStatus().name();
        this.operator = history.getOperator();
        this.reason = history.getReason();
        this.errorMessage = history.getErrorMessage();
        this.payloadModified = history.isPayloadModified();
        this.originalPayload = history.getOriginalPayload();
        this.modifiedPayload = history.getModifiedPayload();
        this.handledAt = history.getHandledAt();
    }
}
