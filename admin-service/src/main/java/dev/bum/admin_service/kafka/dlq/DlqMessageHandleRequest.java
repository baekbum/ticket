package dev.bum.admin_service.kafka.dlq;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DlqMessageHandleRequest(
        @NotBlank String dltTopic,
        @NotNull @Min(0) Integer partition,
        @NotNull @Min(0) Long offset,
        @NotBlank @Size(max = 100) String operator,
        @NotBlank @Size(max = 500) String reason
) {
}
