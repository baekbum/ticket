package dev.bum.payment_gateway_service.jpa.gateway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GatewayPaymentHistoryJpaRepository extends JpaRepository<GatewayPaymentHistory, Long> {

    long countByPaymentPgPaymentId(UUID pgPaymentId);
}
