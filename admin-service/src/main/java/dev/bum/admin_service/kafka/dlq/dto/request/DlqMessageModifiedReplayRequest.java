package dev.bum.admin_service.kafka.dlq.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DlqMessageModifiedReplayRequest {

    // 실패 메시지가 저장된 DLT 토픽.
    @NotBlank
    private String dltTopic;

    // Kafka 파티션 번호.
    @NotNull
    @Min(0)
    private Integer partition;

    // 조회 시작 위치 또는 메시지 오프셋.
    @NotNull
    @Min(0)
    private Long offset;

    // 작업 수행자.
    @NotBlank
    @Size(max = 100)
    private String operator;

    // 처리 사유.
    @NotBlank
    @Size(max = 500)
    private String reason;

    // 수정한 메시지 본문.
    @NotBlank
    private String modifiedPayload;

    // 전달받은 값으로 실패 메시지 객체를 초기화한다.
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
