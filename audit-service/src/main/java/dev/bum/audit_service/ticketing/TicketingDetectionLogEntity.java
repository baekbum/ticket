package dev.bum.audit_service.ticketing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Entity
@Table(
        name = "ticketing_detection_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ticketing_detection_log_session_type",
                columnNames = {"event_id", "user_id", "session_key_hash", "detection_type"}
        ),
        indexes = {
                @Index(name = "idx_ticketing_detection_log_event_occurred_at", columnList = "event_id, occurred_at DESC"),
                @Index(name = "idx_ticketing_detection_log_user_occurred_at", columnList = "user_id, occurred_at DESC")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketingDetectionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "session_key_hash", nullable = false, length = 64)
    private String sessionKeyHash;

    @Column(name = "detection_type", nullable = false, length = 50)
    private String detectionType;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnDefault("'{}'")
    private Map<String, Object> evidence;

    @Column(name = "occurred_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @CreationTimestamp(source = SourceType.DB)
    private Instant createdAt;

    @Builder
    public TicketingDetectionLogEntity(
            Long eventId,
            String userId,
            String sessionKeyHash,
            String detectionType,
            String requestId,
            Map<String, Object> evidence,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.sessionKeyHash = sessionKeyHash;
        this.detectionType = detectionType;
        this.requestId = requestId;
        this.evidence = evidence == null ? Map.of() : new LinkedHashMap<>(evidence);
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
