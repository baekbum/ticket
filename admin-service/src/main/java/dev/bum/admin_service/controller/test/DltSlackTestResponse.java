package dev.bum.admin_service.controller.test;

import java.time.OffsetDateTime;

public record DltSlackTestResponse(
        // 전송 완료 여부.
        boolean sent,

        // 처리를 건너뛴 사유.
        String skippedReason,

        // 실패하기 전 원본 토픽.
        String originTopic,

        // 실패 메시지가 저장된 DLT 토픽.
        String dltTopic,

        // 관리자 DLQ 화면 URL.
        String adminDlqUrl,

        // 요청 시각.
        OffsetDateTime requestedAt
) {
}
