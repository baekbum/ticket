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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "redis_key", nullable = false, length = 200)
    private String redisKey;

    @Column(name = "lock_value", nullable = false, length = 500)
    private String lockValue;

    @Column(nullable = false, length = 100)
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatCacheSyncOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

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

    public static String keyOf(Seat seat) {
        return String.format("event:%d:seat:%s:%d:%d",
                seat.getEvent().getEventId(), seat.getZone().replace(" ", "_"),
                seat.getSeatRow(), seat.getSeatCol());
    }

    public void applied(LocalDateTime now) {
        status = SeatCacheSyncOutboxStatus.APPLIED;
        appliedAt = now;
        lastErrorType = null;
    }

    public void failed(String errorType, LocalDateTime nextAttemptAt) {
        retryCount = Math.incrementExact(retryCount);
        lastErrorType = errorType;
        this.nextAttemptAt = nextAttemptAt;
    }
}
