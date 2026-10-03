package dev.bum.auth_service.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserLoginLockJpaRepository extends JpaRepository<UserLoginLock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserLoginLock> findByAuthId(Long authId);
}
