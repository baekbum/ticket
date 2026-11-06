package dev.bum.payment_gateway_service.jpa.gateway;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayPaymentHistoryJpaRepository extends JpaRepository<GatewayPaymentHistory, Long> {

    long countByPaymentPgPaymentId(String pgPaymentId);
}
