package dev.bum.auth_service.audit;

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
    private Long id;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 50)
    private String serviceName;

    @Column(nullable = false, length = 30)
    private String actorType;

    @Column(length = 50)
    private String actorId;

    @Column(length = 100)
    private String actorName;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 50)
    private String targetType;

    @Column(length = 100)
    private String targetId;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(length = 500)
    private String reason;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 100)
    private String requestId;

    @Column(length = 100)
    private String traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> afterData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(nullable = false)
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
