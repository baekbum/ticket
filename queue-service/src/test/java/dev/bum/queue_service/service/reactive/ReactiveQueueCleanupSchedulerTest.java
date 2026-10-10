package dev.bum.queue_service.service.reactive;

import dev.bum.queue_service.scheduler.QueueCleanupScheduler;
import dev.bum.queue_service.scheduler.ReactiveQueueCleanupScheduler;
import dev.bum.queue_service.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

class ReactiveQueueCleanupSchedulerTest {
    @Test
    void only_reactive_scheduler_runs_without_overlap_and_recovers_on_next_tick() {
        new ApplicationContextRunner().withUserConfiguration(SchedulingTestConfig.class)
                .withPropertyValues("app.queue.cleanup-mode=reactive", "app.queue.cleanup-fixed-delay-ms=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ReactiveQueueCleanupScheduler.class);
                    assertThat(context).doesNotHaveBean(QueueCleanupScheduler.class);
                    AtomicInteger started = new AtomicInteger();
                    AtomicInteger finished = new AtomicInteger();
                    AtomicInteger running = new AtomicInteger();
                    AtomicInteger maximum = new AtomicInteger();
                    var queue = context.getBean(ReactiveQueueService.class);
                    when(queue.cleanupExpiredTokens()).thenReturn(Mono.defer(() -> {
                        int iteration = started.incrementAndGet();
                        maximum.accumulateAndGet(running.incrementAndGet(), Math::max);
                        // 첫 실행은 실패해도 다음 주기의 정리가 계속되어야 한다.
                        Mono<Void> outcome = iteration == 1
                                ? Mono.error(new DataAccessResourceFailureException("첫 정리 실행 실패")) : Mono.empty();
                        return Mono.delay(Duration.ofMillis(80)).then(outcome).doOnTerminate(() -> {
                            running.decrementAndGet();
                            finished.incrementAndGet();
                        });
                    }));
                    await().atMost(Duration.ofSeconds(5)).until(() -> finished.get() >= 3);
                    assertThat(maximum.get()).isEqualTo(1);
                    verifyNoInteractions(context.getBean(QueueService.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import({QueueCleanupScheduler.class, ReactiveQueueCleanupScheduler.class})
    static class SchedulingTestConfig {
        @Bean ReactiveQueueService reactiveQueueService() {
            var queue = mock(ReactiveQueueService.class);
            when(queue.cleanupExpiredTokens()).thenReturn(Mono.empty());
            return queue;
        }
        @Bean QueueService queueService() { return mock(QueueService.class); }
    }
}
