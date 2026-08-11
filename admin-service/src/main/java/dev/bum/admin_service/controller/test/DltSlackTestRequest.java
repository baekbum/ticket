package dev.bum.admin_service.controller.test;

import jakarta.validation.constraints.NotBlank;

public record DltSlackTestRequest(
        @NotBlank String originTopic,
        @NotBlank String dltTopic,
        Integer originPartition,
        Integer dltPartition,
        Long offset,
        String key,
        @NotBlank String payload,
        String exceptionMessage
) {
}
