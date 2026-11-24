package dev.bum.audit_service.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {

    @Modifying
    @Query(value = """
            insert into audit_log (
                idempotency_key, request_hash, occurred_at, service_name, actor_type, actor_id, actor_name,
                action, target_type, target_id, result, reason, ip_address, user_agent, request_id, trace_id,
                before_data, after_data, metadata, created_at
            ) values (
                :#{#entry.idempotencyKey}, :#{#entry.requestHash}, :#{#entry.occurredAt}, :#{#entry.serviceName},
                :#{#entry.actorType}, :#{#entry.actorId}, :#{#entry.actorName}, :#{#entry.action},
                :#{#entry.targetType}, :#{#entry.targetId}, :#{#entry.result}, :#{#entry.reason},
                :#{#entry.ipAddress}, :#{#entry.userAgent}, :#{#entry.requestId}, :#{#entry.traceId},
                cast(:beforeData as jsonb), cast(:afterData as jsonb), cast(:metadata as jsonb), :#{#entry.createdAt}
            ) on conflict (service_name, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("entry") AuditLogEntity entry,
            @Param("beforeData") String beforeData,
            @Param("afterData") String afterData,
            @Param("metadata") String metadata
    );

    Optional<AuditLogEntity> findByServiceNameAndIdempotencyKey(String serviceName, String idempotencyKey);
}
