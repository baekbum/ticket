package dev.bum.ticket_service.jpa.reservation.reservation;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationJpaRepository extends JpaRepository<Reservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.reservationId = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") long id);

    // 외부 취소 요청은 같은 예매가 처리 중이면 기다리지 않고 즉시 거부한다.
    @Query(value = "select r.* from reservations r where r.reservation_id = :id for update nowait", nativeQuery = true)
    Optional<Reservation> findByIdForUpdateNowait(@Param("id") long id);
}
