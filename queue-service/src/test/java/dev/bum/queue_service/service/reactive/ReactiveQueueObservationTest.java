package dev.bum.queue_service.service.reactive;

import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReactiveQueueObservationTest {
    @Test
    void observation_covers_subscription_until_error_and_closes_on_cancellation() {
        var registry = ObservationRegistry.create();
        AtomicInteger started = new AtomicInteger();
        AtomicInteger stopped = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            public boolean supportsContext(Observation.Context context) { return true; }
            public void onStart(Observation.Context context) { started.incrementAndGet(); }
            public void onStop(Observation.Context context) { stopped.incrementAndGet(); }
            public void onError(Observation.Context context) { error.set(context.getError()); }
        });
        var waiting = mock(ReactiveWaitingQueueService.class);
        var active = mock(ReactiveActiveQueueService.class);
        when(waiting.pruneExpiredWaitingTokens(1L))
                .thenReturn(Mono.delay(Duration.ofMillis(30)).then());
        when(active.pruneExpiredActiveTokens(1L)).thenReturn(Mono.empty());
        when(active.admit(1L, "user", "missing")).thenReturn(Mono.error(new QueueTokenInvalidException("유효하지 않은 토큰")));
        var queue = new ReactiveQueueService(mock(ReactiveStringRedisTemplate.class), registry,
                new QueueProperties(), waiting, active, mock(ReactiveUserQueueSessionService.class));
        var operation = queue.status(1L, "user", "missing");
        assertThat(started.get()).isZero();
        StepVerifier.create(operation)
                .then(() -> {
                    assertThat(started.get()).isEqualTo(1);
                    assertThat(stopped.get()).isZero();
                }).expectError(QueueTokenInvalidException.class).verify(Duration.ofSeconds(3));
        assertThat(stopped.get()).isEqualTo(1);
        assertThat(error.get()).isInstanceOf(QueueTokenInvalidException.class);

        when(waiting.pruneExpiredWaitingTokens(1L)).thenReturn(Mono.never());
        StepVerifier.create(queue.status(1L, "user", "missing"))
                .then(() -> assertThat(stopped.get()).isEqualTo(1))
                .thenCancel().verify(Duration.ofSeconds(3));
        assertThat(started.get()).isEqualTo(2);
        assertThat(stopped.get()).isEqualTo(2);
    }
}
