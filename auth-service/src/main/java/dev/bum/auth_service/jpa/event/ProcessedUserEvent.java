package dev.bum.auth_service.jpa.event;

import dev.bum.common.kafka.enums.TopicEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 회원 DB 반영과 같은 트랜잭션으로 저장한다. Redis 후처리 성공 여부와는 구분한다. */
@Entity
@Table(name = "processed_user_events",
        indexes = @Index(name = "idx_processed_user_events_user", columnList = "user_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_user_events_auth_key",
                columnNames = {"auth_id", "idempotency_key"}))
@Getter
@NoArgsConstructor
public class ProcessedUserEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "processed_user_event_id")
    private Long processedUserEventId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "auth_id", nullable = false)
    private Long authId;

    @Column(name = "event_version", nullable = false)
    private long eventVersion;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private TopicEventType eventType;

    // 동일 키의 요청 내용이 바뀌었는지 검증한다. 비밀번호 등 payload는 저장하지 않는다.
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
