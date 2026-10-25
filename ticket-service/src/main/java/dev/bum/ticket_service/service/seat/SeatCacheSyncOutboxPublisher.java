package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutbox;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutboxJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatCacheSyncOutboxPublisher {
    private final SeatCacheSyncOutboxJpaRepository repository;
    private final SeatJpaRepository seats;
    private final StringRedisTemplate seatRedisTemplate;

    @Value("${app.seat-cache.sync-outbox.retry-delay-ms:30000}")
    private long retryDelayMs = 30000;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long outboxId) {
        SeatCacheSyncOutbox outbox = repository.findByIdForUpdate(outboxId).orElse(null);

        if (outbox == null || outbox.getStatus() == SeatCacheSyncOutboxStatus.APPLIED
                || outbox.getNextAttemptAt().isAfter(LocalDateTime.now())) return;

        try {
            // 지연된 옛 작업도 최신 DB 상태를 반영한다. Redis 적용이 끝날 때까지 좌석 행을 잠근다.
            Seat seat = seats.findByIdForUpdate(outbox.getSeatId())
                    .orElseThrow(() -> new IllegalStateException("동기화할 좌석을 찾을 수 없습니다."));
            if (!outbox.getRedisKey().equals(SeatCacheSyncOutbox.keyOf(seat))) {
                throw new IllegalStateException("동기화 대상 좌석의 좌표가 변경되었습니다.");
            }
            Long result = seatRedisTemplate.execute(SeatCacheSyncScripts.APPLY,
                    List.of(outbox.getRedisKey(), outbox.getRedisKey() + ":lock"),
                    String.valueOf(ttl(seat).toMillis()), seat.getStatus().name(), outbox.getLockValue());
            if (Long.valueOf(0L).equals(result)) {
                outbox.failed("OCCUPIED_BY_ANOTHER_RESERVATION", nextAttemptAt());
                return;
            }
            if (!Long.valueOf(1L).equals(result)) {
                throw new DataRetrievalFailureException("좌석 Redis 동기화 결과를 확인할 수 없습니다.");
            }
            outbox.applied(LocalDateTime.now());
        } catch (RuntimeException e) {
            // 오류 메시지에는 소유 정보 등이 포함될 수 있으므로 유형만 저장한다.
            outbox.failed(e.getClass().getSimpleName(), nextAttemptAt());
            log.warn("좌석 캐시 Outbox 적용 실패: outboxId={}, seatId={}, errorType={}",
                    outboxId, outbox.getSeatId(), e.getClass().getSimpleName());
        }
    }

    private LocalDateTime nextAttemptAt() {
        return LocalDateTime.now().plus(Duration.ofMillis(retryDelayMs));
    }

    private Duration ttl(Seat seat) {
        if (seat.getEvent().getEventDateTime() == null) return Duration.ofDays(7);
        int runningMinutes = seat.getEvent().getRunningMinutes() == null ? 0 : seat.getEvent().getRunningMinutes();
        Duration ttl = Duration.between(LocalDateTime.now(), seat.getEvent().getEventDateTime().plusMinutes(runningMinutes));
        return ttl.isPositive() ? ttl : Duration.ofMinutes(1);
    }
}
