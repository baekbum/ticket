package dev.bum.queue_service.service.reactive;

import dev.bum.queue_service.config.QueueProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.Mockito.*;

class ReactiveQueueServiceErrorTest {
    @Test
    void redis_failure_is_deferred_and_propagated_without_recovery_mutations() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        var waiting = mock(ReactiveWaitingQueueService.class);
        var active = mock(ReactiveActiveQueueService.class);
        var sessions = mock(ReactiveUserQueueSessionService.class);
        var failure = new DataAccessResourceFailureException("테스트 Redis 장애");
        when(waiting.pruneExpiredWaitingTokens(1L, "user")).thenReturn(Mono.error(failure));
        when(active.pruneExpiredActiveTokens(1L)).thenReturn(Mono.empty());
        when(waiting.isWaitingTokenValid(1L, "user", "token")).thenReturn(Mono.just(true));
        var queue = new ReactiveQueueService(redis, new QueueProperties(), waiting, active, sessions);
        var request = queue.status(1L, "user", "token");
        clearInvocations(waiting, active);
        verifyNoInteractions(waiting, active, sessions, redis);
        StepVerifier.create(request).expectErrorMatches(error -> error == failure).verify(Duration.ofSeconds(3));
        verify(waiting, never()).refreshWaitingToken(anyLong(), anyString(), anyString());
        verify(active, never()).admit(anyLong(), anyString(), anyString());
    }
}
