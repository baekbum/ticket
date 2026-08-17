package dev.bum.payment_gateway_service.jpa.virtualAccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DummyVirtualAccountJpaRepository extends JpaRepository<DummyVirtualAccount, Long> {

    Optional<DummyVirtualAccount> findByPaymentNo(String paymentNo);

    boolean existsByAccountNumber(String accountNumber);

    List<DummyVirtualAccount> findTop100ByStatusOrderByDepositedAtAsc(VirtualAccountPaymentStatus status);
}
