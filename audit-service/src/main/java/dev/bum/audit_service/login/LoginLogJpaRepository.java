package dev.bum.audit_service.login;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LoginLogJpaRepository extends JpaRepository<LoginLogEntity, Long>, JpaSpecificationExecutor<LoginLogEntity> {
}
