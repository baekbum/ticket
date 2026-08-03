package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailure;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SeatCacheSyncFailureServiceTest {

    @Mock
    private SeatCacheSyncFailureJpaRepository repository;

    @Test
    @DisplayName("좌석 Redis 동기화 실패 정보를 보정 이력으로 저장")
    void record_failure() {
        SeatCacheSyncFailureService service = new SeatCacheSyncFailureService(repository);
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
}
