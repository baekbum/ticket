package dev.bum.audit_service.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {
    Optional<AuditLogEntity> findByServiceNameAndIdempotencyKey(String serviceName, String idempotencyKey);
}
