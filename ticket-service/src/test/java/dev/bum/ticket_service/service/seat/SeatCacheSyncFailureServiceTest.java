package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailure;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureStatus;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SeatCacheSyncFailureServiceTest {

    @Mock
    private SeatCacheSyncFailureJpaRepository repository;

    @Mock
    private StringRedisTemplate seatRedisTemplate;

    @Mock
    private SeatJpaRepository seatRepository;

    @Test
    @DisplayName("좌석 Redis 동기화 실패 정보를 보정 이력으로 저장")
    void record_failure() {
        SeatCacheSyncFailureService service = new SeatCacheSyncFailureService(repository, seatRedisTemplate, seatRepository);
        DataAccessException exception = new DataAccessException("redis error") {};

        service.recordFailure(
                "syncReservedSeatsAfterCommit",
                "event:{eventId}:seat",
                List.of("event:1:seat:VIP:1:1"),
                List.of("RESERVED"),
                exception
        );

        ArgumentCaptor<SeatCacheSyncFailure> captor = ArgumentCaptor.forClass(SeatCacheSyncFailure.class);
        then(repository).should().save(captor.capture());

        SeatCacheSyncFailure failure = captor.getValue();
        assertThat(failure.getOperation()).isEqualTo("syncReservedSeatsAfterCommit");
        assertThat(failure.getKeyPrefix()).isEqualTo("event:{eventId}:seat");
        assertThat(failure.getRedisKeys()).isEqualTo("event:1:seat:VIP:1:1");
        assertThat(failure.getTargetValue()).isEqualTo("RESERVED");
        assertThat(failure.getFailureMessage()).isEqualTo("redis error");
        assertThat(failure.getStatus()).isEqualTo(SeatCacheSyncFailureStatus.PENDING);
        assertThat(failure.getRetryCount()).isZero();
        assertThat(failure.getCreatedAt()).isNotNull();
        assertThat(failure.getLastFailedAt()).isNotNull();
    }

    @Test
    @DisplayName("여러 실패 좌석을 전달해도 좌석별 이력으로 나누어 저장한다")
    void record_each_seat_separately() {
        SeatCacheSyncFailureService service = new SeatCacheSyncFailureService(repository, seatRedisTemplate, seatRepository);
        service.recordFailure("cancel", "event:{eventId}:seat",
                List.of("event:1:seat:VIP:1:2", "event:1:seat:VIP:1:3"),
                List.of("AVAILABLE"), new IllegalStateException("redis error"));

        ArgumentCaptor<SeatCacheSyncFailure> captor = ArgumentCaptor.forClass(SeatCacheSyncFailure.class);
        then(repository).should(times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SeatCacheSyncFailure::getRedisKeys)
                .containsExactly("event:1:seat:VIP:1:2", "event:1:seat:VIP:1:3");
    }

    @Test
    @DisplayName("재처리는 실패 당시 값 대신 현재 DB 좌석 상태를 반영한다")
    void retry_uses_current_database_status() {
        SeatCacheSyncFailureService service = new SeatCacheSyncFailureService(repository, seatRedisTemplate, seatRepository);
        SeatCacheSyncFailure failure = pendingFailure("RESERVED");
        given(repository.findByIdForUpdate(1L)).willReturn(Optional.of(failure));
        given(seatRepository.findByCacheCoordinatesForUpdate(1L, "VIP", 1, 1))
                .willReturn(List.of(Seat.builder().status(SeatStatus.AVAILABLE).build()));
        given(seatRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), eq("AVAILABLE")))
                .willReturn(1L);

        service.retry(1L);

        assertThat(failure.getStatus()).isEqualTo(SeatCacheSyncFailureStatus.RESOLVED);
        then(seatRedisTemplate).should().execute(any(DefaultRedisScript.class),
                eq(List.of("event:1:seat:VIP:1:1", "event:1:seat:VIP:1:1:lock")), eq("604800000"), eq("AVAILABLE"));
        then(seatRedisTemplate).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("현재 사용자 선점이 있으면 재처리를 해결 처리하지 않는다")
    void retry_keeps_pending_when_current_occupation_exists() {
        SeatCacheSyncFailureService service = new SeatCacheSyncFailureService(repository, seatRedisTemplate, seatRepository);
        SeatCacheSyncFailure failure = pendingFailure("AVAILABLE");
        given(repository.findByIdForUpdate(1L)).willReturn(Optional.of(failure));
        given(seatRepository.findByCacheCoordinatesForUpdate(1L, "VIP", 1, 1))
                .willReturn(List.of(Seat.builder().status(SeatStatus.AVAILABLE).build()));
        given(seatRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .willReturn(0L);

        assertThatThrownBy(() -> service.retry(1L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("현재 좌석 선점");
        assertThat(failure.getStatus()).isEqualTo(SeatCacheSyncFailureStatus.PENDING);
        assertThat(failure.getRetryCount()).isEqualTo(1);
        then(seatRedisTemplate).should().execute(any(DefaultRedisScript.class),
                eq(List.of("event:1:seat:VIP:1:1", "event:1:seat:VIP:1:1:lock")), anyString(), eq("AVAILABLE"));
        then(seatRedisTemplate).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("재처리 중 Redis 장애가 발생하면 실패 횟수를 기록하고 PENDING을 유지한다")
    void retry_records_redis_failure() {
        SeatCacheSyncFailureService service = new SeatCacheSyncFailureService(repository, seatRedisTemplate, seatRepository);
        SeatCacheSyncFailure failure = pendingFailure("AVAILABLE");
        given(repository.findByIdForUpdate(1L)).willReturn(Optional.of(failure));
        given(seatRepository.findByCacheCoordinatesForUpdate(1L, "VIP", 1, 1))
                .willReturn(List.of(Seat.builder().status(SeatStatus.AVAILABLE).build()));
        given(seatRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .willThrow(new DataAccessException("redis unavailable") {});

        assertThatThrownBy(() -> service.retry(1L)).isInstanceOf(DataAccessException.class);
        assertThat(failure.getStatus()).isEqualTo(SeatCacheSyncFailureStatus.PENDING);
        assertThat(failure.getRetryCount()).isEqualTo(1);
        assertThat(failure.getFailureMessage()).isEqualTo("redis unavailable");
    }

    private SeatCacheSyncFailure pendingFailure(String targetValue) {
        return SeatCacheSyncFailure.builder().operation("syncAvailableSeatsAfterCommit")
                .keyPrefix("event:{eventId}:seat").redisKeys("event:1:seat:VIP:1:1")
                .targetValue(targetValue).failureMessage("redis error").build();
    }
}
