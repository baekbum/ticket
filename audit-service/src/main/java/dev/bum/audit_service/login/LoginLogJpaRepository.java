package dev.bum.audit_service.login;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginLogJpaRepository extends JpaRepository<LoginLogEntity, Long> {
}
