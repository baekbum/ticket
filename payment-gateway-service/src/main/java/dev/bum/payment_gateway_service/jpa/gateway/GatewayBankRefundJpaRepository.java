package dev.bum.payment_gateway_service.jpa.gateway;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface GatewayBankRefundJpaRepository extends JpaRepository<GatewayBankRefund, Long> {

    Optional<GatewayBankRefund> findByPaymentPgPaymentIdAndRefundRequestId(String pgPaymentId, String refundRequestId);

    List<GatewayBankRefund> findByPaymentPgPaymentIdAndStatus(String pgPaymentId, String status);
}
