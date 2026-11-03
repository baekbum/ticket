package dev.bum.admin_service.kafka.dlq.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "dlq_messages", uniqueConstraints = @UniqueConstraint(
        name = "uk_dlq_message_source", columnNames = {"dlt_topic", "partition_no", "message_offset"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DlqMessage {

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
    @Column(columnDefinition = "bytea")
    private byte[] messageKey;

    // 메시지 본문.
    @Column(columnDefinition = "bytea")
    private byte[] payload;

    // JSON으로 직렬화한 메시지 헤더.
    @Column(nullable = false, columnDefinition = "text")
    private String headersJson;

    // Kafka 레코드 생성 시각.
    @Column(nullable = false)
    private long recordTimestamp;

    // 실패 메시지 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DlqMessageStatus status;

    // 현재 처리 이력 식별자.
    private Long currentHistoryId;

    // 마지막 수정 시각.
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 실패 메시지 처리에서 처리를 시작한다.
    public void start(Long historyId) {

        if (status != DlqMessageStatus.PENDING && status != DlqMessageStatus.FAILED
                && status != DlqMessageStatus.UNKNOWN) {
            throw new IllegalStateException("이미 처리 중이거나 처리된 DLT 메시지입니다.");
        }
        currentHistoryId = historyId;
        changeStatus(DlqMessageStatus.REPLAYING);
    }

    // 처리 상태를 변경한다.
    public void changeStatus(DlqMessageStatus status) {

        this.status = status;
        updatedAt = LocalDateTime.now();
    }
}
