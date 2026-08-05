package dev.bum.ticket_service.jpa.seat.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SeatCacheSyncFailureJpaRepository extends JpaRepository<SeatCacheSyncFailure, Long>, JpaSpecificationExecutor<SeatCacheSyncFailure> {
}
