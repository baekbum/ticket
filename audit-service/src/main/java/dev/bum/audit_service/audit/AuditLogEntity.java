package dev.bum.audit_service.audit;

import dev.bum.common.kafka.audit.AuditLogEvent;
import dev.bum.common.service.audit.dto.AuditLogResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "audit_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_audit_log_service_idempotency", columnNames = {"service_name", "idempotency_key"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntity {

    // 고유 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 중복 처리를 방지하는 멱등성 키.
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    // 요청 내용의 일치 여부를 확인하는 해시.
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    // 이벤트 발생 시각.
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    // 이벤트가 발생한 서비스 이름.
    @Column(nullable = false, length = 50)
    private String serviceName;

    // 작업 수행자 유형.
    @Column(nullable = false, length = 30)
    private String actorType;

    // 작업 수행자 식별자.
    @Column(length = 50)
    private String actorId;

    // 작업 수행자 이름.
    @Column(length = 100)
    private String actorName;

    // 수행한 작업.
    @Column(nullable = false, length = 100)
    private String action;

    // 작업 대상 유형.
    @Column(length = 50)
    private String targetType;

    // 작업 대상 식별자.
    @Column(length = 100)
    private String targetId;

    // 처리 결과.
    @Column(nullable = false, length = 20)
    private String result;

    // 처리 사유.
    @Column(length = 500)
    private String reason;

    // 요청자의 IP 주소.
    @Column(length = 45)
    private String ipAddress;

    // 요청 클라이언트 정보.
    @Column(length = 500)
    private String userAgent;

    // 요청 추적 식별자.
    @Column(length = 100)
    private String requestId;

    // 분산 추적 식별자.
    @Column(length = 100)
    private String traceId;

    // 변경 전 데이터.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> beforeData;

    // 변경 후 데이터.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> afterData;

    // 부가 정보.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // 생성 시각.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 전달받은 값으로 감사 로그 객체를 초기화한다.
    @Builder
    public AuditLogEntity(
            String idempotencyKey,
            String requestHash,
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
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
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

    // 원본 데이터를 감사 로그 객체로 변환한다.
    public static AuditLogEntity from(AuditLogEvent event, String requestHash) {

        return AuditLogEntity.builder()
                .idempotencyKey(event.getIdempotencyKey())
                .requestHash(requestHash)
                .occurredAt(event.getOccurredAt())
                .serviceName(event.getServiceName())
                .actorType(event.getActorType())
                .actorId(event.getActorId())
                .actorName(event.getActorName())
                .action(event.getAction())
                .targetType(event.getTargetType())
                .targetId(event.getTargetId())
                .result(event.getResult())
                .reason(event.getReason())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .requestId(event.getRequestId())
                .traceId(event.getTraceId())
                .beforeData(event.getBeforeData())
                .afterData(event.getAfterData())
                .metadata(event.getMetadata())
                .build();
    }

    // 외부 응답에 사용할 DTO로 변환한다.
    public AuditLogResponse toResponse() {

        return AuditLogResponse.builder()
                .id(id)
                .occurredAt(occurredAt)
                .serviceName(serviceName)
                .actorType(actorType)
                .actorId(actorId)
                .actorName(actorName)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .result(result)
                .reason(reason)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestId(requestId)
                .traceId(traceId)
                .beforeData(beforeData)
                .afterData(afterData)
                .metadata(metadata)
                .createdAt(createdAt)
                .build();
    }
}
