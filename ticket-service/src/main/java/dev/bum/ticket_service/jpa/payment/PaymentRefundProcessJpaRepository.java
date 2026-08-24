package dev.bum.ticket_service.jpa.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundProcessJpaRepository extends JpaRepository<PaymentRefundProcess, Long> {
}
