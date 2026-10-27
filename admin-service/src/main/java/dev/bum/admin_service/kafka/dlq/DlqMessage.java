package dev.bum.admin_service.kafka.dlq;

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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String dltTopic;
    @Column(nullable = false)
    private int partitionNo;
    @Column(nullable = false)
    private long messageOffset;
    @Column(columnDefinition = "bytea")
    private byte[] messageKey;
    @Column(columnDefinition = "bytea")
    private byte[] payload;
    @Column(nullable = false, columnDefinition = "text")
    private String headersJson;
    @Column(nullable = false)
    private long recordTimestamp;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DlqMessageStatus status;
    private Long currentHistoryId;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void start(Long historyId) {
        if (status != DlqMessageStatus.PENDING && status != DlqMessageStatus.FAILED
                && status != DlqMessageStatus.UNKNOWN) {
            throw new IllegalStateException("이미 처리 중이거나 처리된 DLT 메시지입니다.");
        }
        currentHistoryId = historyId;
        changeStatus(DlqMessageStatus.REPLAYING);
    }

    public void changeStatus(DlqMessageStatus status) {
        this.status = status;
        updatedAt = LocalDateTime.now();
    }
}
