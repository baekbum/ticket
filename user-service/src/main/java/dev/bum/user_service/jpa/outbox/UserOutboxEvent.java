package dev.bum.user_service.jpa.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_outbox_events", indexes = {
        @Index(name = "idx_user_outbox_pending", columnList = "status,next_attempt_at,outbox_id"),
        @Index(name = "idx_user_outbox_order", columnList = "user_id,status,outbox_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    // 재시도에도 최초 이벤트 내용을 그대로 발행한다. 회원 삭제 후에도 기록을 유지한다.
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private UserOutboxStatus status = UserOutboxStatus.PENDING;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 100)
    private String lastError;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void published(LocalDateTime now) {
        attemptCount++;
        status = UserOutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    public void failed(String errorType, LocalDateTime retryAt) {
        attemptCount++;
        lastError = errorType.substring(0, Math.min(errorType.length(), 100));
        nextAttemptAt = retryAt;
    }
}
