package dev.bum.payment_gateway_service.jpa.virtualAccount;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankVirtualAccountCancellationJpaRepository extends JpaRepository<BankVirtualAccountCancellation, String> {
}
