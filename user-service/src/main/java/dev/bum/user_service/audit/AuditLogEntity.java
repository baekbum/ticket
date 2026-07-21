package dev.bum.user_service.audit;

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
    // 감사 로그 PK
    private Long id;

    @Column(nullable = false)
    // 실제 감사 이벤트가 발생한 시각
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 50)
    // 이벤트가 발생한 서비스 이름
    private String serviceName;

    @Column(nullable = false, length = 30)
    // 행위자 유형: USER, ADMIN, SYSTEM, ANONYMOUS
    private String actorType;

    @Column(length = 50)
    // 행위자 식별자
    private String actorId;

    @Column(length = 100)
    // 행위자 표시 이름
    private String actorName;

    @Column(nullable = false, length = 100)
    // 수행한 행위 코드
    private String action;

    @Column(length = 50)
    // 행위 대상 유형
    private String targetType;

    @Column(length = 100)
    // 행위 대상 식별자
    private String targetId;

    @Column(nullable = false, length = 20)
    // 처리 결과: SUCCESS, FAILURE
    private String result;

    @Column(length = 500)
    // 실패 사유 또는 처리 사유
    private String reason;

    @Column(length = 45)
    // 요청 IP 주소
    private String ipAddress;

    @Column(length = 500)
    // 요청 클라이언트 User-Agent
    private String userAgent;

    @Column(length = 100)
    // 요청 단위 추적 ID
    private String requestId;

    @Column(length = 100)
    // 분산 추적 ID
    private String traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    // 변경 전 데이터
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    // 변경 후 데이터
    private Map<String, Object> afterData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    // 추가 컨텍스트 데이터
    private Map<String, Object> metadata;

    @Column(nullable = false)
    // 감사 로그 레코드 생성 시각
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
