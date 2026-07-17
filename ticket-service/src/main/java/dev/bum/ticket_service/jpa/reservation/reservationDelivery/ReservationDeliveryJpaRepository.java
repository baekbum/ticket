package dev.bum.ticket_service.jpa.reservation.reservationDelivery;

import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ReservationDeliveryJpaRepository extends JpaRepository<ReservationDelivery, Long>, JpaSpecificationExecutor<ReservationDelivery> {

    Optional<ReservationDelivery> findByReservation(Reservation reservation);

    boolean existsByReservation(Reservation reservation);
}
