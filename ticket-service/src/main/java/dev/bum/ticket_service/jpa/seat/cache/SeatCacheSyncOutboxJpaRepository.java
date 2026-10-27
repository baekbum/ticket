package dev.bum.ticket_service.jpa.seat.cache;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatCacheSyncOutboxJpaRepository extends JpaRepository<SeatCacheSyncOutbox, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SeatCacheSyncOutbox o where o.outboxId = :id")
    Optional<SeatCacheSyncOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o.outboxId from SeatCacheSyncOutbox o
            where o.status = :status and o.nextAttemptAt <= :now
            order by o.nextAttemptAt, o.outboxId
            """)
    List<Long> findReadyIds(@Param("status") SeatCacheSyncOutboxStatus status,
                           @Param("now") LocalDateTime now, Pageable pageable);
}
