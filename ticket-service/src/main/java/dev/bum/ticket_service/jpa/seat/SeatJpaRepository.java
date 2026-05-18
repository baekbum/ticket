package dev.bum.ticket_service.jpa.seat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatJpaRepository extends JpaRepository<Seat, Long> {
    // Seat -> event(필드) -> eventId(필드) 순서로 매칭
    long countByEventEventId(Long eventId);
    List<Seat> findAllBySeatIdIn(List<Long> seatIdList);
}
