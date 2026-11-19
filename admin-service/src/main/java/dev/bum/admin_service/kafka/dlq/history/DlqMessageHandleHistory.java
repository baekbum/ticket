package dev.bum.admin_service.kafka.dlq.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "dlq_message_handle_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DlqMessageHandleHistory {

    // 고유 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 실패 메시지가 저장된 DLT 토픽.
    @Column(nullable = false, length = 150)
    private String dltTopic;

    // Kafka 파티션 번호.
    @Column(nullable = false)
    private int partitionNo;

    // Kafka 메시지 오프셋.
    @Column(nullable = false)
    private long messageOffset;

    // 메시지 키.
    @Column(length = 500)
    private String messageKey;

    // 메시지를 발행할 대상 토픽.
    @Column(length = 150)
    private String targetTopic;

    // 수행한 작업.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DlqMessageHandleAction action;

    // 실패 메시지 처리 이력 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DlqMessageHandleStatus status;

    // 작업 수행자.
    @Column(nullable = false, length = 100)
    private String operator;

    // 처리 사유.
    @Column(length = 500)
    private String reason;

    // 오류 메시지.
    @Column(columnDefinition = "text")
    private String errorMessage;

    // 수정 전 메시지 본문.
    @Column(columnDefinition = "text")
    private String originalPayload;

    // 수정한 메시지 본문.
    @Column(columnDefinition = "text")
    private String modifiedPayload;

    // 메시지 본문 수정 여부.
    @Column(nullable = false)
    private boolean payloadModified;

    // 처리 시각.
    @Column(nullable = false)
    private LocalDateTime handledAt;

    // 실패 메시지 처리 이력 처리에서 완료 상태로 변경하고 결과를 반영한다.
    public void complete(DlqMessageHandleStatus status, String errorMessage) {

        this.status = status;
        this.errorMessage = errorMessage;
    }

    // 전달받은 값으로 실패 메시지 처리 이력 객체를 초기화한다.
    @Builder
    public DlqMessageHandleHistory(
            String dltTopic,
            int partitionNo,
            long messageOffset,
            String messageKey,
            String targetTopic,
            DlqMessageHandleAction action,
            DlqMessageHandleStatus status,
            String operator,
            String reason,
            String errorMessage,
            String originalPayload,
            String modifiedPayload,
            boolean payloadModified
    ) {

        this.dltTopic = dltTopic;
        this.partitionNo = partitionNo;
        this.messageOffset = messageOffset;
        this.messageKey = messageKey;
        this.targetTopic = targetTopic;
        this.action = action;
        this.status = status;
        this.operator = operator;
        this.reason = reason;
        this.errorMessage = errorMessage;
        this.originalPayload = originalPayload;
        this.modifiedPayload = modifiedPayload;
        this.payloadModified = payloadModified;
        this.handledAt = LocalDateTime.now();
    }
}
