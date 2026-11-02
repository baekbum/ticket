package dev.bum.ticket_service.jpa.checkout;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CheckoutAttemptJpaRepository
        extends JpaRepository<CheckoutAttempt, String>, CheckoutAttemptInsertRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ca from CheckoutAttempt ca where ca.orderId = :orderId")
    Optional<CheckoutAttempt> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ca from CheckoutAttempt ca where ca.idempotencyKey = :idempotencyKey")
    Optional<CheckoutAttempt> findByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey
    );
}
