package dev.bum.admin_service.kafka.dlq.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DlqMessageHandleHistoryCondRequest {

    // 실패 메시지가 저장된 DLT 토픽.
    private String dltTopic;

    // Kafka 파티션 번호.
    private Integer partitionNo;

    // Kafka 메시지 오프셋.
    private Long messageOffset;

    // 메시지 키.
    private String messageKey;

    // 수행한 작업.
    private String action;

    // 실패 메시지 처리 이력 상태.
    private String status;

    // 작업 수행자.
    private String operator;

    // 메시지 본문 수정 여부.
    private Boolean payloadModified;

    // 처리 시각 조회 시작 조건.
    private LocalDateTime handledFrom;

    // 처리 시각 조회 종료 조건.
    private LocalDateTime handledTo;

    // 조회할 페이지.
    private Integer page;

    // 페이지당 조회 건수.
    private Integer size;
}
