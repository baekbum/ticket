package dev.bum.auth_service.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface AuthJpaRepository extends JpaRepository<Auth, Long> {
    Optional<Auth> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auth a where a.id = :id")
    Optional<Auth> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auth a where a.userId = :userId")
    Optional<Auth> findByUserIdForUpdate(@Param("userId") String userId);
}
