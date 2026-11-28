package dev.bum.audit_service.ticketing;

import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import dev.bum.common.kafka.ticketing.TicketingDetectionType;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.type.SqlTypes;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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

    private static final Pattern SESSION_KEY_HASH = Pattern.compile("[0-9a-fA-F]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "session_key_hash", nullable = false, length = 64)
    private String sessionKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_type", nullable = false, length = 50)
    private TicketingDetectionType detectionType;

    @Column(name = "trace_id", length = 100)
    private String traceId;

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
            TicketingDetectionType detectionType,
            String traceId,
            Map<String, Object> evidence,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.sessionKeyHash = sessionKeyHash;
        this.detectionType = detectionType;
        this.traceId = traceId;
        this.evidence = evidence == null ? Map.of() : new LinkedHashMap<>(evidence);
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static TicketingDetectionLogEntity from(TicketingDetectionLogEvent event) {

        if (event == null || event.getEventId() == null || event.getOccurredAt() == null
                || !StringUtils.hasText(event.getUserId()) || event.getDetectionType() == null
                || event.getSessionKeyHash() == null || !SESSION_KEY_HASH.matcher(event.getSessionKeyHash()).matches()) {
            throw new IllegalArgumentException("티켓팅 탐지 이벤트의 회차·사용자·세션 해시·감지 유형·발생 시각이 올바르지 않습니다.");
        }

        return builder()
                .eventId(event.getEventId())
                .userId(event.getUserId())
                .sessionKeyHash(event.getSessionKeyHash().toLowerCase(Locale.ROOT))
                .detectionType(event.getDetectionType())
                .traceId(event.getTraceId())
                .occurredAt(event.getOccurredAt())
                .evidence(event.getEvidence())
                .build();
    }
}
