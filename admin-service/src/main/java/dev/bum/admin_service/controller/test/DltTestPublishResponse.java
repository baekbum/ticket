package dev.bum.admin_service.controller.test;

import java.time.OffsetDateTime;

public record DltTestPublishResponse(
        // 실패 메시지가 저장된 DLT 토픽.
        String dltTopic,

        // Kafka 파티션 번호.
        Integer partition,

        // 조회 시작 위치 또는 메시지 오프셋.
        Long offset,

        // 데이터 식별 키.
        String key,

        // 발행 시각.
        OffsetDateTime publishedAt
) {
}
