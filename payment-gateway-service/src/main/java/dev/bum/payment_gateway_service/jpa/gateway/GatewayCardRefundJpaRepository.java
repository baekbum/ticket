package dev.bum.payment_gateway_service.jpa.gateway;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface GatewayCardRefundJpaRepository extends JpaRepository<GatewayCardRefund, Long> {

    Optional<GatewayCardRefund> findByPaymentPgPaymentIdAndRefundRequestId(String pgPaymentId, String refundRequestId);

    List<GatewayCardRefund> findByPaymentPgPaymentIdAndStatus(String pgPaymentId, String status);
}
