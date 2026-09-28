package dev.bum.ticket_service.jpa.checkout;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutAttemptJpaRepository extends JpaRepository<CheckoutAttempt, String> {
}
