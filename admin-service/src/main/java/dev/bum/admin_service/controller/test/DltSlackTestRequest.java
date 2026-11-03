package dev.bum.admin_service.controller.test;

import jakarta.validation.constraints.NotBlank;

public record DltSlackTestRequest(
        // 실패하기 전 원본 토픽.
        @NotBlank String originTopic,

        // 실패 메시지가 저장된 DLT 토픽.
        @NotBlank String dltTopic,

        // 원본 메시지의 Kafka 파티션 번호.
        Integer originPartition,

        // 실패 메시지의 Kafka 파티션 번호.
        Integer dltPartition,

        // 조회 시작 위치 또는 메시지 오프셋.
        Long offset,

        // 데이터 식별 키.
        String key,

        // 메시지 본문.
        @NotBlank String payload,

        // 발생한 예외 메시지.
        String exceptionMessage
) {
}
