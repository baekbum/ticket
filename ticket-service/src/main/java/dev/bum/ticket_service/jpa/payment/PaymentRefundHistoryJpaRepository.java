package dev.bum.ticket_service.jpa.payment;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface PaymentRefundHistoryJpaRepository extends JpaRepository<PaymentRefundHistory, Long> {

    @EntityGraph(attributePaths = {"tickets", "tickets.ticket"})
    List<PaymentRefundHistory> findByReservationOrderByPaymentRefundHistoryIdDesc(Reservation reservation);
}
