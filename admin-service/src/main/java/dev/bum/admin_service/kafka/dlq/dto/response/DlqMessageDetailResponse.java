package dev.bum.admin_service.kafka.dlq.dto.response;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class DlqMessageDetailResponse {

    // 실패 메시지가 저장된 DLT 토픽.
    private final String dltTopic;

    // Kafka 파티션 번호.
    private final int partition;

    // 조회 시작 위치 또는 메시지 오프셋.
    private final long offset;

    // 메시지를 발행할 대상 토픽.
    private final String targetTopic;

    // 메시지 키.
    private final String messageKey;

    // 메시지 본문.
    private final String payload;

    // Base64로 인코딩한 메시지 본문.
    private final String payloadBase64;

    // 메시지 헤더 목록.
    private final List<DlqHeaderResponse> headers;

    // 이벤트 발생 시각.
    private final LocalDateTime occurredAt;

    // 작업 진행 상태.
    private final String processingStatus;

    // 실패 메시지에 수행한 작업.
    private final String handleAction;

    // 실패 메시지 처리 상태.
    private final String handleStatus;

    // 실패 메시지 처리 담당자.
    private final String handledOperator;

    // 실패 메시지 처리 사유.
    private final String handleReason;

    // 실패 메시지 처리 중 발생한 오류.
    private final String handleErrorMessage;

    // 처리 시각.
    private final LocalDateTime handledAt;

    // 메시지 본문 수정 여부.
    private final boolean payloadModified;

    // 수정 전 메시지 본문.
    private final String originalPayload;

    // 수정한 메시지 본문.
    private final String modifiedPayload;

    // 전달받은 값으로 실패 메시지 객체를 초기화한다.
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
            LocalDateTime handledAt,
            boolean payloadModified,
            String originalPayload,
            String modifiedPayload
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
        this.payloadModified = payloadModified;
        this.originalPayload = originalPayload;
        this.modifiedPayload = modifiedPayload;
    }
}
