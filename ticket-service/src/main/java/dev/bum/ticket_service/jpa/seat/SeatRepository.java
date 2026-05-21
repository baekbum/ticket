package dev.bum.ticket_service.jpa.seat;

import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.SeatCond;
import dev.bum.ticket_service.vo.seat.UpdateSeatInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SeatRepository {
    void insert(InsertSeatInfo info);
    void isExist(SeatCond cond);
    long countByEventId(Long eventId);
    Seat selectById(Long id);
    List<Seat> selectByEventId(Long eventId);
    List<Seat> selectByIdList(List<Long> seatIdList);
    Page<Seat> selectByCond(SeatCond cond, Pageable pageable);
    void update(UpdateSeatInfo info);
    void delete(Long id);
}
