package dev.bum.auth_service.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogPersistenceService {

    private final AuditLogJpaRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditLogEntity auditLog) {
        repository.save(auditLog);
    }
}
