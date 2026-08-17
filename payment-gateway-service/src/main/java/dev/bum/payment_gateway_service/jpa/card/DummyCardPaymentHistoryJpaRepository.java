package dev.bum.payment_gateway_service.jpa.card;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DummyCardPaymentHistoryJpaRepository extends JpaRepository<DummyCardPaymentHistory, Long> {

    boolean existsByPaymentNo(String paymentNo);
}
