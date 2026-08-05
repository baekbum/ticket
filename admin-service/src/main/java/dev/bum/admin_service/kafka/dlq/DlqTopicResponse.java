package dev.bum.admin_service.kafka.dlq;

import lombok.Getter;

@Getter
public class DlqTopicResponse {

    private final String dltTopic;
    private final String targetTopic;

    public DlqTopicResponse(String dltTopic, String targetTopic) {
        this.dltTopic = dltTopic;
        this.targetTopic = targetTopic;
    }
}
