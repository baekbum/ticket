package dev.bum.payment_gateway_service.jpa.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VirtualAccountOutboxEventJpaRepository extends JpaRepository<VirtualAccountOutboxEvent, Long> {

    List<VirtualAccountOutboxEvent> findTop100ByStatusOrderByOutboxIdAsc(OutboxEventStatus status);
}
