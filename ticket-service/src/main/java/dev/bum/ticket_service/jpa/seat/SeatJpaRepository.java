package dev.bum.ticket_service.jpa.seat;

import dev.bum.ticket_service.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatJpaRepository extends JpaRepository<Seat, Long> {
    // Seat -> event(필드) -> eventId(필드) 순서로 매칭
    long countByEventEventId(long eventId);

    List<Seat> findByEventEventId(long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
    @Query("select s from Seat s where s.seatId in :seatIdList and s.status = :status")
    List<Seat> findAllBySeatIdInAndStatus(
            @Param("seatIdList") List<Long> seatIdList,
            @Param("status") SeatStatus status
    );
}
