package dev.bum.admin_service.kafka.dlq.dto.response;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class DlqMessageSummaryResponse {

    // 실패 메시지가 저장된 DLT 토픽.
    private final String dltTopic;

    // Kafka 파티션 번호.
    private final int partition;

    // 조회 시작 위치 또는 메시지 오프셋.
    private final long offset;

    // 메시지 키.
    private final String messageKey;

    // 메시지 본문 미리보기.
    private final String payloadPreview;

    // 메시지 헤더 목록.
    private final List<DlqHeaderResponse> headers;

    // 이벤트 발생 시각.
    private final LocalDateTime occurredAt;

    // 작업 진행 상태.
    private final String processingStatus;

    // 전달받은 값으로 실패 메시지 객체를 초기화한다.
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
