package dev.bum.ticket_service.jpa.reservation.reservationDelivery;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationDeliveryJpaRepository extends JpaRepository<ReservationDelivery, Long> {

    Optional<ReservationDelivery> findByReservation(Reservation reservation);
}
