package dev.bum.ticket_service.jpa.seat.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SeatCacheSyncFailureJpaRepository extends JpaRepository<SeatCacheSyncFailure, Long>, JpaSpecificationExecutor<SeatCacheSyncFailure> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SeatCacheSyncFailure f where f.id = :id")
    Optional<SeatCacheSyncFailure> findByIdForUpdate(@Param("id") Long id);
}
