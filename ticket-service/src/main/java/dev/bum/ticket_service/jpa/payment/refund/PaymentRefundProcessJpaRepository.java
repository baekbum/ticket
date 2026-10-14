package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;

public interface PaymentRefundProcessJpaRepository
        extends JpaRepository<PaymentRefundProcess, Long>,
                JpaSpecificationExecutor<PaymentRefundProcess> {

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select p from PaymentRefundProcess p where p.refund.payment.reservation=:reservation"
                + " and p.status in :statuses and p.paymentRefundProcessId=(select"
                + " max(p2.paymentRefundProcessId) from PaymentRefundProcess p2 where"
                + " p2.refund.payment.reservation=:reservation and p2.status in :statuses)")
    Optional<PaymentRefundProcess>
            findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(
                    @org.springframework.data.repository.query.Param("reservation")
                            Reservation reservation,
                    @org.springframework.data.repository.query.Param("statuses")
                            Collection<PaymentRefundProcessStatus> statuses);
}
