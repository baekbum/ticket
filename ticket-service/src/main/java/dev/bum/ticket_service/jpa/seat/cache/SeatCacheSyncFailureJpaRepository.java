package dev.bum.ticket_service.jpa.seat.cache;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatCacheSyncFailureJpaRepository extends JpaRepository<SeatCacheSyncFailure, Long> {
}
