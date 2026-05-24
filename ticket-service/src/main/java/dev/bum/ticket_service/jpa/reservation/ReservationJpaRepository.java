package dev.bum.ticket_service.jpa.reservation;

import dev.bum.ticket_service.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationJpaRepository extends JpaRepository<Reservation, Long> {
//    @Query("SELECT COUNT(r) FROM Reservation r " +
//            "WHERE r.event.eventId = :eventId " +
//            "AND r.userId = :userId " +
//            "AND r.seat.status <> :status")
//    long countReservedSeats(
//            @Param("eventId") Long eventId,
//            @Param("userId") String userId,
//            @Param("status") SeatStatus status
//    );
}
