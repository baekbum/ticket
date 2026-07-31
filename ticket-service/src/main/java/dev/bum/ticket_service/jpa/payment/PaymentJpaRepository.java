package dev.bum.ticket_service.jpa.payment;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentNo = :paymentNo")
    Optional<Payment> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByReservation(Reservation reservation);

    boolean existsByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.accountNumber = :accountNumber")
    Optional<Payment> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
