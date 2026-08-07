package dev.bum.admin_service.kafka.dlq;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DlqMessageModifiedReplayRequest {

    @NotBlank
    private String dltTopic;

    @NotNull
    @Min(0)
    private Integer partition;

    @NotNull
    @Min(0)
    private Long offset;

    @NotBlank
    @Size(max = 100)
    private String operator;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotBlank
    private String modifiedPayload;

    public DlqMessageModifiedReplayRequest(
            String dltTopic,
            Integer partition,
            Long offset,
            String operator,
            String reason,
            String modifiedPayload
    ) {
        this.dltTopic = dltTopic;
        this.partition = partition;
        this.offset = offset;
        this.operator = operator;
        this.reason = reason;
        this.modifiedPayload = modifiedPayload;
    }
}
