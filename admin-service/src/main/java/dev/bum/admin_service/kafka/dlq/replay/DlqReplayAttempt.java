package dev.bum.admin_service.kafka.dlq.replay;

import lombok.Builder;

@Builder
public record DlqReplayAttempt(
        // 처리 이력 식별자.
        Long historyId,

        // 메시지를 발행할 대상 토픽.
        String targetTopic,

        // 데이터 식별 키.
        byte[] key,

        // 메시지 본문.
        byte[] payload,

        // JSON으로 직렬화한 메시지 헤더.
        String headersJson
) {}
