package dev.bum.ticket_service.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Entity
@Table(name = "audit_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Audit log primary key
    private Long id;

    @Column(nullable = false)
    // Time when the audited event occurred
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 50)
    // Service that produced this event
    private String serviceName;

    @Column(nullable = false, length = 30)
    // Actor type: USER, ADMIN, SYSTEM, ANONYMOUS
    private String actorType;

    @Column(length = 50)
    // Actor identifier
    private String actorId;

    @Column(length = 100)
    // Actor display name
    private String actorName;

    @Column(nullable = false, length = 100)
    // Action code
    private String action;

    @Column(length = 50)
    // Target domain type
    private String targetType;

    @Column(length = 100)
    // Target identifier
    private String targetId;

    @Column(nullable = false, length = 20)
    // Result: SUCCESS, FAILURE
    private String result;

    @Column(length = 500)
    // Failure or processing reason
    private String reason;

    @Column(length = 45)
    // Client IP address
    private String ipAddress;

    @Column(length = 500)
    // Request User-Agent
    private String userAgent;

    @Column(length = 100)
    // Request correlation ID
    private String requestId;

    @Column(length = 100)
    // Distributed trace ID
    private String traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    // Data before update
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    // Data after update
    private Map<String, Object> afterData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    // Additional context data
    private Map<String, Object> metadata;

    @Column(nullable = false)
    // Record creation time
    private LocalDateTime createdAt;

    @Builder
    public AuditLogEntity(
            LocalDateTime occurredAt,
            String serviceName,
            String actorType,
            String actorId,
            String actorName,
            String action,
            String targetType,
            String targetId,
            String result,
            String reason,
            String ipAddress,
            String userAgent,
            String requestId,
            String traceId,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            Map<String, Object> metadata
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.occurredAt = occurredAt != null ? occurredAt : now;
        this.serviceName = serviceName;
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.reason = reason;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.traceId = traceId;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.metadata = metadata;
        this.createdAt = now;
    }
}
