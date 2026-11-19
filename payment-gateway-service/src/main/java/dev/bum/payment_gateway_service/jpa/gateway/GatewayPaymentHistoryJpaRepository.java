package dev.bum.payment_gateway_service.jpa.gateway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GatewayPaymentHistoryJpaRepository extends JpaRepository<GatewayPaymentHistory, Long> {

    long countByPaymentPgPaymentId(String pgPaymentId);

    Optional<GatewayPaymentHistory> findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(
            String pgPaymentId,
            GatewayPaymentOperation operationType,
            String operationKey
    );
}
