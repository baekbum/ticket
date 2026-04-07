package dev.bum.auth_service.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthJpaRepository extends JpaRepository<Auth, Long> {
    Optional<Auth> findByUserId(String userId);
}
