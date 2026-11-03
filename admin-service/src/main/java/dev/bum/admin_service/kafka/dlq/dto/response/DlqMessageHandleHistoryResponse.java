package dev.bum.admin_service.kafka.dlq.dto.response;

import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleHistory;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class DlqMessageHandleHistoryResponse {

    // 고유 식별자.
    private final Long id;

    // 실패 메시지가 저장된 DLT 토픽.
    private final String dltTopic;

    // Kafka 파티션 번호.
    private final int partitionNo;

    // Kafka 메시지 오프셋.
    private final long messageOffset;

    // 메시지 키.
    private final String messageKey;

    // 메시지를 발행할 대상 토픽.
    private final String targetTopic;

    // 수행한 작업.
    private final String action;

    // 실패 메시지 처리 이력 상태.
    private final String status;

    // 작업 수행자.
    private final String operator;

    // 처리 사유.
    private final String reason;

    // 오류 메시지.
    private final String errorMessage;

    // 메시지 본문 수정 여부.
    private final boolean payloadModified;

    // 수정 전 메시지 본문.
    private final String originalPayload;

    // 수정한 메시지 본문.
    private final String modifiedPayload;

    // 처리 시각.
    private final LocalDateTime handledAt;

    // 전달받은 값으로 실패 메시지 처리 이력 객체를 초기화한다.
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
