package dev.bum.admin_service.kafka.dlq.dto.response;

import lombok.Getter;

@Getter
public class DlqMessageHandleResponse {

    // 처리 결과.
    private final String result;

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

    // 작업 수행자.
    private final String operator;

    // 처리 사유.
    private final String reason;

    // 전달받은 값으로 실패 메시지 객체를 초기화한다.
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
