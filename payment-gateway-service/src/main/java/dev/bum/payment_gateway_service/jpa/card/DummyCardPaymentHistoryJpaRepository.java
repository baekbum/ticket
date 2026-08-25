package dev.bum.payment_gateway_service.jpa.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DummyCardPaymentHistoryJpaRepository extends JpaRepository<DummyCardPaymentHistory, Long> {

    Optional<DummyCardPaymentHistory> findByPaymentNo(String paymentNo);

    Optional<DummyCardPaymentHistory> findByPaymentNoAndTransactionId(String paymentNo, String transactionId);
}
