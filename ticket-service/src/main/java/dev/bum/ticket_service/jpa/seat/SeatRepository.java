package dev.bum.ticket_service.jpa.seat;

import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.SeatCond;
import dev.bum.ticket_service.vo.seat.UpdateSeatInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SeatRepository {
    Seat insert(InsertSeatInfo info);
    void isExist(SeatCond cond);
    Seat selectById(Long id);
    Page<Seat> selectByCond(SeatCond cond, Pageable pageable);
    Seat update(Long id, UpdateSeatInfo info);
    Seat delete(Long id);
}
