package dev.bum.ticket_service.jpa.reservation;

import dev.bum.ticket_service.vo.reservation.CancelReservationInfo;
import dev.bum.ticket_service.vo.reservation.InsertReservationInfo;
import dev.bum.ticket_service.vo.reservation.ReservationCond;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReservationRepository {
    Reservation insert(InsertReservationInfo info);
    Reservation selectById(long id);
    Page<Reservation> selectByCond(ReservationCond cond, Pageable pageable);
    void cancel(long id, CancelReservationInfo info);
    void isReservable(String userId, long eventId, int selectedSeatCnt);
}
