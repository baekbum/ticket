package dev.bum.admin_service.controller.test;

import jakarta.validation.constraints.NotBlank;

public record DltTestPublishRequest(
        @NotBlank String dltTopic,
        String key,
        @NotBlank String payload
) {
}
