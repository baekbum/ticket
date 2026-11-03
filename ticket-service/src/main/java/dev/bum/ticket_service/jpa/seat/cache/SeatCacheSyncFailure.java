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

    // 고유 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 수행할 작업 종류.
    @Column(nullable = false, length = 100)
    private String operation;

    // Redis 키 접두사.
    @Column(nullable = false, length = 50)
    private String keyPrefix;

    // 조회하거나 처리할 Redis 키 목록.
    @Column(nullable = false, columnDefinition = "text")
    private String redisKeys;

    // 처리 대상 값.
    @Column(nullable = false, length = 50)
    private String targetValue;

    // 실패 상세 메시지.
    @Column(columnDefinition = "text")
    private String failureMessage;

    // 좌석 캐시 동기화 실패 정보 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatCacheSyncFailureStatus status;

    // 재시도 횟수.
    @Column(nullable = false)
    private int retryCount;

    // 생성 시각.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 마지막 실패 시각.
    @Column(nullable = false)
    private LocalDateTime lastFailedAt;

    // 문제 해결 시각.
    @Column
    private LocalDateTime resolvedAt;

    // 문제 해결 내용.
    @Column(columnDefinition = "text")
    private String resolvedMessage;

    // 전달받은 값으로 좌석 캐시 동기화 실패 정보 객체를 초기화한다.
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

    // 좌석 캐시 동기화 실패 정보 처리에서 현재 상태에 맞는 후속 처리 또는 해결 결과를 결정한다.
    public void resolve(String resolvedMessage) {

        this.status = SeatCacheSyncFailureStatus.RESOLVED;
        this.resolvedMessage = resolvedMessage;
        this.resolvedAt = LocalDateTime.now();
    }

    // 실패 메시지를 폐기 상태로 변경한다.
    public void discard(String resolvedMessage) {

        this.status = SeatCacheSyncFailureStatus.DISCARDED;
        this.resolvedMessage = resolvedMessage;
        this.resolvedAt = LocalDateTime.now();
    }

    // 재시도 실패 사유와 다음 실행 정보를 기록한다.
    public void failRetry(String failureMessage) {

        this.retryCount++;
        this.failureMessage = failureMessage;
        this.lastFailedAt = LocalDateTime.now();
    }
}
