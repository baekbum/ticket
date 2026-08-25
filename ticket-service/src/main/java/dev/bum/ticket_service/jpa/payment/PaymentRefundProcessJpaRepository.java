package dev.bum.ticket_service.jpa.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;

import java.util.Collection;
import java.util.Optional;

public interface PaymentRefundProcessJpaRepository extends JpaRepository<PaymentRefundProcess, Long>, JpaSpecificationExecutor<PaymentRefundProcess> {

    Optional<PaymentRefundProcess> findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(
            Reservation reservation,
            Collection<PaymentRefundProcessStatus> statuses
    );
}
