package dev.bum.ticket_service.jpa.payment.payment;

import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "reservation")
    Page<Payment> findByMethod(PaymentMethod method, Pageable pageable);

    @EntityGraph(attributePaths = "reservation")
    Page<Payment> findByMethodAndStatus(PaymentMethod method, PaymentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "reservation")
    Page<Payment> findByMethodAndPaymentNoContainingIgnoreCase(
            PaymentMethod method,
            String paymentNo,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "reservation")
    Page<Payment> findByMethodAndStatusAndPaymentNoContainingIgnoreCase(
            PaymentMethod method,
            PaymentStatus status,
            String paymentNo,
            Pageable pageable
    );

    Optional<Payment> findByPaymentNo(String paymentNo);

    // 기한은 조회 후보 선정에만 사용한다. 실제 만료는 PG의 확정 상태를 확인한 후 처리한다.
    @Query("""
            select p.paymentId from Payment p
            where p.paymentId > :afterId and p.gatewayBankCompany is not null
              and p.method = :method
              and p.status in :statuses
              and p.gatewayExpiresAt <= :now
            order by p.paymentId
            """)
    List<Long> findGatewayExpirationCandidateIds(
            @Param("now") LocalDateTime now,
            @Param("afterId") Long afterId,
            @Param("method") PaymentMethod method,
            @Param("statuses") Collection<PaymentStatus> statuses,
            Pageable page
    );

    @Query("select p.reservation.reservationId from Payment p where p.paymentNo = :paymentNo")
    Optional<Long> findReservationIdByPaymentNo(@Param("paymentNo") String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentNo = :paymentNo")
    @Observed(name = "ticket.repository.payment.find-by-payment-no-for-update", contextualName = "ticket repository payment find by payment no for update")
    Optional<Payment> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    Optional<Payment> findByReservation(Reservation reservation);

    @Query("select count(p) > 0 from Payment p where p.virtualAccountInfo.accountNumber = :accountNumber")
    boolean existsByAccountNumber(@Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.virtualAccountInfo.accountNumber = :accountNumber")
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
              and not (p.method = :bankTransferMethod
                       and (p.status = :waitingDepositStatus
                            or p.gatewayBankCompany is not null))
              and p.expiresAt is not null
              and p.expiresAt <= :now
            order by p.expiresAt asc, p.paymentId asc
            """)
    @Observed(name = "ticket.repository.payment.find-expired-payment-ids", contextualName = "ticket repository payment find expired payment ids")
    List<Long> findExpiredPaymentIds(
            @Param("statuses") Collection<PaymentStatus> statuses,
            @Param("bankTransferMethod") PaymentMethod bankTransferMethod,
            @Param("waitingDepositStatus") PaymentStatus waitingDepositStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
