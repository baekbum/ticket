package dev.bum.ticket_service.jpa.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentRefundProcessJpaRepository extends JpaRepository<PaymentRefundProcess, Long>, JpaSpecificationExecutor<PaymentRefundProcess> {
}
