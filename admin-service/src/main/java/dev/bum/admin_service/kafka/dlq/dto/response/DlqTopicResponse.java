package dev.bum.admin_service.kafka.dlq.dto.response;

import lombok.Getter;

@Getter
public class DlqTopicResponse {

    // 실패 메시지가 저장된 DLT 토픽.
    private final String dltTopic;

    // 메시지를 발행할 대상 토픽.
    private final String targetTopic;

    // 전달받은 값으로 처리 정보 객체를 초기화한다.
    public DlqTopicResponse(String dltTopic, String targetTopic) {

        this.dltTopic = dltTopic;
        this.targetTopic = targetTopic;
    }
}
