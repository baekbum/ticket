package dev.bum.audit_service.audit;

import dev.bum.audit_service.kafka.event.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogPersistenceService {

    private final AuditLogJpaRepository repository;

    @Transactional
    public AuditLogEntity save(AuditLogEvent event) {
        return repository.save(AuditLogEntity.from(event));
    }
}
