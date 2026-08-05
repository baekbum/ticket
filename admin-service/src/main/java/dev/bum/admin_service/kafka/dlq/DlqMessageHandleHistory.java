package dev.bum.admin_service.kafka.dlq;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String dltTopic;

    @Column(nullable = false)
    private int partitionNo;

    @Column(nullable = false)
    private long messageOffset;

    @Column(length = 500)
    private String messageKey;

    @Column(length = 150)
    private String targetTopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DlqMessageHandleAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DlqMessageHandleStatus status;

    @Column(nullable = false, length = 100)
    private String operator;

    @Column(length = 500)
    private String reason;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(columnDefinition = "text")
    private String originalPayload;

    @Column(columnDefinition = "text")
    private String modifiedPayload;

    @Column(nullable = false)
    private boolean payloadModified;

    @Column(nullable = false)
    private LocalDateTime handledAt;

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
