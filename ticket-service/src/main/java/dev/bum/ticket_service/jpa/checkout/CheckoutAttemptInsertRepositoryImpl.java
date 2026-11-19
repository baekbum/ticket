package dev.bum.ticket_service.jpa.checkout;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CheckoutAttemptInsertRepositoryImpl implements CheckoutAttemptInsertRepository {

    private final EntityManager entityManager;

    @Override
    public int insertIfAbsent(CheckoutAttempt attempt) {

        int inserted = entityManager.createNativeQuery("""
                        INSERT INTO checkout_attempts (
                            idempotency_key, user_id, order_id, event_id, payment_no,
                            status, expires_at, created_at, updated_at
                        ) VALUES (
                            :idempotencyKey, :userId, :orderId, :eventId, :paymentNo,
                            :status, :expiresAt, :createdAt, :createdAt
                        )
                        ON CONFLICT (order_id) DO NOTHING
                        """)
                .setParameter("idempotencyKey", attempt.getIdempotencyKey())
                .setParameter("userId", attempt.getUserId())
                .setParameter("orderId", attempt.getOrderId())
                .setParameter("eventId", attempt.getEventId())
                .setParameter("paymentNo", attempt.getPaymentNo())
                .setParameter("status", attempt.getStatus().name())
                .setParameter("expiresAt", attempt.getExpiresAt())
                .setParameter("createdAt", attempt.getCreatedAt())
                .executeUpdate();

        if (inserted == 1) {
            for (Long seatId : attempt.getSeatIds().stream().sorted().toList()) {
                entityManager.createNativeQuery("""
                                INSERT INTO checkout_attempt_seats (idempotency_key, seat_id)
                                VALUES (:idempotencyKey, :seatId)
                                """)
                        .setParameter("idempotencyKey", attempt.getIdempotencyKey())
                        .setParameter("seatId", seatId)
                        .executeUpdate();
            }
        }

        return inserted;
    }
}
