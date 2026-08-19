package dev.bum.payment_gateway_service.jpa.virtualAccount;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface DummyVirtualAccountJpaRepository extends JpaRepository<DummyVirtualAccount, Long> {

    Optional<DummyVirtualAccount> findByPaymentNo(String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DummyVirtualAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<DummyVirtualAccount> findTop100ByStatusOrderByDepositedAtAsc(VirtualAccountPaymentStatus status);

    List<DummyVirtualAccount> findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            VirtualAccountPaymentStatus status,
            LocalDateTime expiresAt
    );
}
