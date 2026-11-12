package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRefundHistoryJpaRepository
        extends JpaRepository<PaymentRefundHistory, Long> {

    @EntityGraph(attributePaths = {"paymentRefundProcess.refund.snapshot.tickets"})
    @Query(
            "select h from PaymentRefundHistory h where"
                + " h.paymentRefundProcess.refund.payment.reservation=:reservation and"
                + " h.eventType=:eventType"
                + " order by h.paymentRefundHistoryId desc")
    List<PaymentRefundHistory> findByReservationOrderByPaymentRefundHistoryIdDesc(
            @Param("reservation") Reservation reservation,
            @Param("eventType") PaymentRefundHistoryType eventType
    );

    @Query(
            "select count(h)>0 from PaymentRefundHistory h where h.paymentRefundProcess=:process"
                + " and h.eventType=:eventType")
    boolean existsByPaymentRefundProcess(
            @Param("process") PaymentRefundProcess process,
            @Param("eventType") PaymentRefundHistoryType eventType
    );
}
