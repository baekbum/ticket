package dev.bum.admin_service.controller.test;

public record DltSlackTestConfigResponse(
        boolean enabled,
        boolean webhookConfigured,
        String adminDlqUrl
) {
}
