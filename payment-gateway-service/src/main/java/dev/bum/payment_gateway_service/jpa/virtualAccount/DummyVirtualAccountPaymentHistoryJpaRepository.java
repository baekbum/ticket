package dev.bum.payment_gateway_service.jpa.virtualAccount;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DummyVirtualAccountPaymentHistoryJpaRepository
        extends JpaRepository<DummyVirtualAccountPaymentHistory, Long> {
}
