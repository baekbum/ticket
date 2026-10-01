package dev.bum.ticket_service.jpa.seat;

import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatJpaRepository extends JpaRepository<Seat, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.event.eventId = :eventId and replace(s.zone, ' ', '_') = :zone and s.seatRow = :row and s.seatCol = :col")
    List<Seat> findByCacheCoordinatesForUpdate(@Param("eventId") Long eventId,
            @Param("zone") String zone, @Param("row") Integer row, @Param("col") Integer col);

    List<Seat> findByEventEventId(long eventId);

    List<Seat> findByAreaAreaId(long areaId);

    long countByAreaAreaId(Long areaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
    @Query("select s from Seat s where s.event.eventId = :eventId and s.seatId in :seatIdList and s.status = :status")
    List<Seat> findAllByEventIdAndSeatIdInAndStatus(
            @Param("eventId") Long eventId,
            @Param("seatIdList") List<Long> seatIdList,
            @Param("status") SeatStatus status
    );

    void deleteBySeatIdIn(List<Long> seatIdList);

    void deleteByEventEventId(Long eventId);

    void deleteByAreaAreaId(Long areaId);
}
