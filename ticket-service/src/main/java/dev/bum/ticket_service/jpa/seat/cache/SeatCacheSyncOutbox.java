package dev.bum.ticket_service.jpa.seat.cache;

import dev.bum.ticket_service.jpa.seat.Seat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat_cache_sync_outbox", indexes =
        @Index(name = "idx_seat_cache_outbox_ready", columnList = "status,next_attempt_at,outbox_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatCacheSyncOutbox {

    // 발행 대기 이벤트 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    // 좌석 식별자.
    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    // Redis 저장 키.
    @Column(name = "redis_key", nullable = false, length = 200)
    private String redisKey;

    // 잠금 소유자를 식별하는 값.
    @Column(name = "lock_value", nullable = false, length = 500)
    private String lockValue;

    // 수행할 작업 종류.
    @Column(nullable = false, length = 100)
    private String operation;

    // 좌석 캐시 동기화 Outbox 이벤트 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatCacheSyncOutboxStatus status;

    // 재시도 횟수.
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    // 다음 재시도 예정 시각.
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    // 마지막 오류 유형.
    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;

    // 생성 시각.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 변경 반영 시각.
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    // 전달받은 값으로 좌석 캐시 동기화 Outbox 이벤트 객체를 초기화한다.
    @Builder
    public SeatCacheSyncOutbox(Long seatId, String redisKey, String lockValue, String operation) {

        this.seatId = seatId;
        this.redisKey = redisKey;
        this.lockValue = lockValue;
        this.operation = operation;
        this.status = SeatCacheSyncOutboxStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.nextAttemptAt = createdAt;
    }

    // 처리 대상을 식별할 저장 키를 구성한다.
    public static String keyOf(Seat seat) {

        return String.format("event:%d:seat:%s:%d:%d",
                seat.getEvent().getEventId(), seat.getZone().replace(" ", "_"),
                seat.getSeatRow(), seat.getSeatCol());
    }

    // 변경 반영 완료 상태를 기록한다.
    public void applied(LocalDateTime now) {

        status = SeatCacheSyncOutboxStatus.APPLIED;
        appliedAt = now;
        lastErrorType = null;
    }

    // 좌석 캐시 동기화 Outbox 이벤트 처리에서 실패 사유와 상태를 반영한다.
    public void failed(String errorType, LocalDateTime nextAttemptAt) {

        retryCount = Math.incrementExact(retryCount);
        lastErrorType = errorType;
        this.nextAttemptAt = nextAttemptAt;
    }
}
