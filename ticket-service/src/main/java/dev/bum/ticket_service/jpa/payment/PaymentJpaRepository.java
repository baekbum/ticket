package dev.bum.ticket_service.jpa.payment;

import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentId = :paymentId")
    @Observed(name = "ticket.repository.payment.find-by-id-for-update", contextualName = "ticket repository payment find by id for update")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);

    @Query("""
            select p.paymentId
            from Payment p
            where p.status in :statuses
              and p.expiresAt is not null
              and p.expiresAt <= :now
            order by p.expiresAt asc, p.paymentId asc
            """)
    @Observed(name = "ticket.repository.payment.find-expired-payment-ids", contextualName = "ticket repository payment find expired payment ids")
    List<Long> findExpiredPaymentIds(
            @Param("statuses") Collection<PaymentStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
