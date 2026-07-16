package dev.bum.ticket_service.jpa.reservation.reservationDiscount;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationDiscountJpaRepository extends JpaRepository<ReservationDiscount, Long> {

    List<ReservationDiscount> findByReservation(Reservation reservation);
}
