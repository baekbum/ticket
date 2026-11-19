package dev.bum.payment_gateway_service.jpa.card;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CardPaymentRefundJpaRepository extends JpaRepository<CardPaymentRefund, Long> {

    Optional<CardPaymentRefund> findByPgPaymentIdAndRefundRequestId(String pgPaymentId, String refundRequestId);
}
