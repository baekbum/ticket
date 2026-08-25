package dev.bum.ticket_service.jpa.reservation.reservation;

import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReservationRepository {
    Reservation insert(InsertReservationRequest info);
    Reservation selectById(long id);
    Page<Reservation> selectByCond(ReservationCondRequest cond, Pageable pageable);
    void validateReservableFromDatabase(String userId, long eventId, int selectedSeatCnt);
}
