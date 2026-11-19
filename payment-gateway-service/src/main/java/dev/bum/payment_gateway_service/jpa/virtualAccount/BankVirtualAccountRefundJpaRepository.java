package dev.bum.payment_gateway_service.jpa.virtualAccount;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BankVirtualAccountRefundJpaRepository extends JpaRepository<BankVirtualAccountRefund, Long> {

    Optional<BankVirtualAccountRefund> findByPgPaymentIdAndRefundRequestId(String pgPaymentId, String refundRequestId);
}
