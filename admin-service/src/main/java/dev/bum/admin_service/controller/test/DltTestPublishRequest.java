package dev.bum.admin_service.controller.test;

import jakarta.validation.constraints.NotBlank;

public record DltTestPublishRequest(
        // 실패 메시지가 저장된 DLT 토픽.
        @NotBlank String dltTopic,

        // 데이터 식별 키.
        String key,

        // 메시지 본문.
        @NotBlank String payload
) {
}
