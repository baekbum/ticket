package dev.bum.ticket_service.jpa.payment;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentNo = :paymentNo")
    @Observed(name = "ticket.repository.payment.find-by-payment-no-for-update", contextualName = "ticket repository payment find by payment no for update")
    Optional<Payment> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByReservation(Reservation reservation);

    boolean existsByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.accountNumber = :accountNumber")
    @Observed(name = "ticket.repository.payment.find-by-account-number-for-update", contextualName = "ticket repository payment find by account number for update")
    Optional<Payment> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
