package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PaymentRefundProcessJpaRepository
        extends JpaRepository<PaymentRefundProcess, Long>,
                JpaSpecificationExecutor<PaymentRefundProcess> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentRefundProcess p where p.paymentRefundProcessId = :id")
    Optional<PaymentRefundProcess> findByIdForUpdate(@Param("id") Long id);

    @Query("select p.refund.payment.reservation.reservationId from PaymentRefundProcess p"
            + " where p.paymentRefundProcessId = :id")
    Optional<Long> findReservationIdById(@Param("id") Long id);

    @Lock(
            LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select p from PaymentRefundProcess p where p.refund.payment.reservation=:reservation"
                + " and p.status in :statuses and p.paymentRefundProcessId=(select"
                + " max(p2.paymentRefundProcessId) from PaymentRefundProcess p2 where"
                + " p2.refund.payment.reservation=:reservation and p2.status in :statuses)")
    Optional<PaymentRefundProcess>
            findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(
                    @Param("reservation")
                            Reservation reservation,
                    @Param("statuses")
                            Collection<PaymentRefundProcessStatus> statuses);
}
