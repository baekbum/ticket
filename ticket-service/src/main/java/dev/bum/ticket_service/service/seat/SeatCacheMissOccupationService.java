package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.exception.seat.SeatAlreadyOccupiedException;
import dev.bum.ticket_service.exception.seat.SeatCacheNotFoundException;
import dev.bum.ticket_service.exception.seat.SeatOccupationFailedException;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 캐시 누락 시 DB 좌석 잠금을 Redis 선점이 끝날 때까지 유지한다. */
@Service
@RequiredArgsConstructor
public class SeatCacheMissOccupationService {
    private final SeatJpaRepository seats;
    private final RedisOperations<String, String> seatRedisTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void occupy(Long eventId, SeatInfo info, String redisKey, String lockValue, Duration ttl) {
        Seat seat = seats.findByIdForUpdate(info.getId())
                .orElseThrow(() -> new SeatCacheNotFoundException("좌석 정보가 존재하지 않습니다."));

        Long seatEventId = seat.getEvent() != null ? seat.getEvent().getEventId() : null;

        if (!eventId.equals(seatEventId) || !Objects.equals(seat.getZone(), info.getZone())
                || !Objects.equals(seat.getSeatRow(), info.getRow())
                || !Objects.equals(seat.getSeatCol(), info.getCol())) {
            throw new SeatOccupationFailedException("좌석 정보가 공연 정보와 일치하지 않습니다.");
        }

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatAlreadyOccupiedException("이미 선점되었거나 예매 완료된 좌석입니다.");
        }

        // DB 조회 중 Redis 상태가 바뀌었더라도 Lua에서 다시 확인한 뒤 선점한다.
        Long result = seatRedisTemplate.execute(SeatOccupationScripts.OCCUPY,
                List.of(redisKey, redisKey + ":lock"), lockValue, String.valueOf(ttl.toMillis()), "1");

        if (result == null) {
            throw new DataRetrievalFailureException("좌석 Redis 선점 결과를 확인할 수 없습니다.");
        }

        if (result != 1L) {
            throw new SeatAlreadyOccupiedException("이미 선점되었거나 예매 완료된 좌석입니다.");
        }
    }
}
