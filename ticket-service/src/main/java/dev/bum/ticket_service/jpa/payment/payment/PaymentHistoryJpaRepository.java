package dev.bum.ticket_service.jpa.payment.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentHistoryJpaRepository extends JpaRepository<PaymentHistory, Long> {}
