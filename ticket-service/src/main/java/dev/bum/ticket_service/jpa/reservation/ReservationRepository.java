package dev.bum.ticket_service.jpa.reservation;

import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.ticket_service.jpa.seat.Seat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReservationRepository {
    Reservation insert(InsertReservationRequest info);
    Reservation selectById(long id);
    Page<Reservation> selectByCond(ReservationCondRequest cond, Pageable pageable);
    List<Seat> cancel(long id, CancelReservationRequest info);
    void validateReservableFromDatabase(String userId, long eventId, int selectedSeatCnt);
}
