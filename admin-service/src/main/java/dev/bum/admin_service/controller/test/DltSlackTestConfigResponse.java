package dev.bum.admin_service.controller.test;

public record DltSlackTestConfigResponse(
        // 기능 활성화 여부.
        boolean enabled,

        // 웹훅 설정 여부.
        boolean webhookConfigured,

        // 관리자 DLQ 화면 URL.
        String adminDlqUrl
) {
}
