package dev.bum.ticket_service.jpa.seat.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "seat_cache_sync_failures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatCacheSyncFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String operation;

    @Column(nullable = false, length = 50)
    private String keyPrefix;

    @Column(nullable = false, columnDefinition = "text")
    private String redisKeys;

    @Column(nullable = false, length = 50)
    private String targetValue;

    @Column(columnDefinition = "text")
    private String failureMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatCacheSyncFailureStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastFailedAt;

    @Builder
    public SeatCacheSyncFailure(
            String operation,
            String keyPrefix,
            String redisKeys,
            String targetValue,
            String failureMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.operation = operation;
        this.keyPrefix = keyPrefix;
        this.redisKeys = redisKeys;
        this.targetValue = targetValue;
        this.failureMessage = failureMessage;
        this.status = SeatCacheSyncFailureStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = now;
        this.lastFailedAt = now;
    }
}
