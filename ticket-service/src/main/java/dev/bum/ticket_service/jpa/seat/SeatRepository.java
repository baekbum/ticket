package dev.bum.ticket_service.jpa.seat;

import dev.bum.common.service.ticket.seat.dto.InsertSeatRequest;
import dev.bum.common.service.ticket.seat.dto.SeatCondRequest;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.seat.dto.UpdateSeatRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SeatRepository {
    void insert(InsertSeatRequest info);
    void isExist(SeatCondRequest cond);
    long countByEventId(Long eventId);
    Seat selectById(Long id);
    List<Seat> selectByEventId(Long eventId);
    List<Seat> selectByAreaId(Long areaId);
    List<Seat> selectBySeatList(List<SeatInfo> seatInfos);
    Page<Seat> selectByCond(SeatCondRequest cond, Pageable pageable);
    void update(UpdateSeatRequest info);
    void delete(Long id);
    void deleteByIdList(List<Long> seatIdList);
    void deleteByAreaId(Long areaId);
}
