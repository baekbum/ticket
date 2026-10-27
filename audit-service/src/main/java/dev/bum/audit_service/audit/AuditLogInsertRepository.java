package dev.bum.audit_service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AuditLogInsertRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public int insertIfAbsent(AuditLogEntity entry) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("idempotencyKey", entry.getIdempotencyKey())
                .addValue("requestHash", entry.getRequestHash())
                .addValue("occurredAt", entry.getOccurredAt())
                .addValue("serviceName", entry.getServiceName())
                .addValue("actorType", entry.getActorType())
                .addValue("actorId", entry.getActorId())
                .addValue("actorName", entry.getActorName())
                .addValue("action", entry.getAction())
                .addValue("targetType", entry.getTargetType())
                .addValue("targetId", entry.getTargetId())
                .addValue("result", entry.getResult())
                .addValue("reason", entry.getReason())
                .addValue("ipAddress", entry.getIpAddress())
                .addValue("userAgent", entry.getUserAgent())
                .addValue("requestId", entry.getRequestId())
                .addValue("traceId", entry.getTraceId())
                .addValue("beforeData", json(entry.getBeforeData()))
                .addValue("afterData", json(entry.getAfterData()))
                .addValue("metadata", json(entry.getMetadata()))
                .addValue("createdAt", entry.getCreatedAt());

        // JPA와 같은 DataSource·트랜잭션을 사용한다. 멱등키 충돌만 생략하고 다른 DB 오류는 전파한다.
        return jdbc.update("""
                insert into audit_log (
                    idempotency_key, request_hash, occurred_at, service_name, actor_type, actor_id, actor_name,
                    action, target_type, target_id, result, reason, ip_address, user_agent, request_id, trace_id,
                    before_data, after_data, metadata, created_at
                ) values (
                    :idempotencyKey, :requestHash, :occurredAt, :serviceName, :actorType, :actorId, :actorName,
                    :action, :targetType, :targetId, :result, :reason, :ipAddress, :userAgent, :requestId, :traceId,
                    cast(:beforeData as jsonb), cast(:afterData as jsonb), cast(:metadata as jsonb), :createdAt
                ) on conflict (service_name, idempotency_key) do nothing
                """, parameters);
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("감사 이벤트 데이터를 저장할 수 없습니다.");
        }
    }
}
