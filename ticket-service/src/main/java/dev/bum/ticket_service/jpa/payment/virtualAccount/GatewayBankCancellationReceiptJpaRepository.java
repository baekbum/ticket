package dev.bum.ticket_service.jpa.payment.virtualAccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GatewayBankCancellationReceiptJpaRepository extends JpaRepository<GatewayBankCancellationReceipt, String> {

    Optional<GatewayBankCancellationReceipt> findByPaymentNo(String paymentNo);
}
