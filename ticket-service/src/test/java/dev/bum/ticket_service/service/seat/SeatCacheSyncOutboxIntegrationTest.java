package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutbox;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutboxJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutboxStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@ContextConfiguration(classes = SeatCacheSyncOutboxIntegrationTest.PersistenceConfig.class)
@Import({SeatCacheSyncOutboxService.class, SeatCacheSyncOutboxPublisher.class, SeatCacheSyncOutboxDispatcher.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SeatCacheSyncOutboxIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.ticket_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.ticket_service.jpa")
    static class PersistenceConfig { }

    @Autowired SeatCacheSyncOutboxService service;
    @Autowired SeatCacheSyncOutboxPublisher publisher;
    @MockitoSpyBean SeatCacheSyncOutboxDispatcher dispatcher;
    @MockitoBean StringRedisTemplate redis;
    @Autowired SeatCacheSyncOutboxJpaRepository outbox;
    @Autowired SeatJpaRepository seats;
    @Autowired EventJpaRepository events;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entityManager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String port = System.getenv("TICKET_SEAT_OUTBOX_POSTGRES_PORT");
        if (port != null && !port.isBlank()) {
            properties.add("spring.datasource.url", () ->
                    "jdbc:postgresql://127.0.0.1:" + Integer.parseInt(port) + "/seat_outbox_test");
            properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            properties.add("spring.datasource.username", () -> "seat_test");
            properties.add("spring.datasource.password", () -> "seat_test");
        } else {
            properties.add("spring.datasource.url", () ->
                    "jdbc:h2:mem:seat-outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            properties.add("spring.datasource.username", () -> "sa");
            properties.add("spring.datasource.password", () -> "");
        }
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        seats.deleteAll();
        events.deleteAll();
        lenient().when(redis.execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
    }

    @Test
    void enqueueRequiresAnExistingBusinessTransaction() {
        assertThatThrownBy(() -> service.enqueue(List.of(), "complete", "user", "order"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void seatAndWorkCommitBeforeRedisIsUpdated() {
        Long seatId = fixture();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Seat seat = seats.findByIdForUpdate(seatId).orElseThrow();
            seat.reserved();
            service.enqueue(List.of(seat), "complete", "user", "order");
            assertThat(outbox.count()).isEqualTo(1);
            verifyNoInteractions(redis);
        });
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(outbox.findAll()).singleElement().satisfies(record ->
                assertThat(record.getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.APPLIED));
        verify(redis).execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), eq("RESERVED"), eq("LOCKED:user:order"));
    }

    @Test
    void businessRollbackRemovesSeatChangeAndWorkWithoutCallingRedis() {
        Long seatId = fixture();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Seat seat = seats.findByIdForUpdate(seatId).orElseThrow();
            seat.reserved();
            service.enqueue(List.of(seat), "complete", "user", "order");
            throw new IllegalStateException("business rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(redis);
    }

    @Test
    void outboxInsertFailureAlsoRollsBackSeatChange() {
        Long seatId = fixture();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Seat seat = seats.findByIdForUpdate(seatId).orElseThrow();
            seat.reserved();
            service.enqueue(List.of(seat), "x".repeat(101), "user", "order");
        })).isInstanceOf(RuntimeException.class);
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(redis);
    }

    @Test
    void schedulerRecoversWorkWhenImmediateDeliveryNeverRan() {
        Long id = enqueueWithoutImmediateApply(fixture(), SeatStatus.RESERVED);
        assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.PENDING);
        verifyNoInteractions(redis);
        doCallRealMethod().when(dispatcher).publishSafely(anyLong());
        dispatcher.retryPendingEvents();
        assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.APPLIED);
    }

    @Test
    void redisFailurePreservesCommittedWorkAndRetriesAfterBackoff() {
        Long id = enqueueWithoutImmediateApply(fixture(), SeatStatus.RESERVED);
        when(redis.execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        publisher.publish(id);
        SeatCacheSyncOutbox pending = outbox.findById(id).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.PENDING);
        assertThat(pending.getRetryCount()).isEqualTo(1);
        assertThat(pending.getLastErrorType()).isEqualTo("RedisConnectionFailureException");
        assertThat(pending.getNextAttemptAt()).isAfter(LocalDateTime.now());
        publisher.publish(id);
        verify(redis, times(1)).execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString());
        makeDue(id);
        doReturn(1L).when(redis).execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString());
        publisher.publish(id);
        assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.APPLIED);
    }

    @Test
    void reorderedOldWorkUsesCurrentDatabaseState() {
        Long seatId = fixture();
        Long old = enqueueWithoutImmediateApply(seatId, SeatStatus.RESERVED);
        Long latest = enqueueWithoutImmediateApply(seatId, SeatStatus.AVAILABLE);
        publisher.publish(latest);
        publisher.publish(old);
        verify(redis, times(2)).execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), eq("AVAILABLE"), eq("LOCKED:user:order"));
        assertThat(outbox.findAll()).allSatisfy(record ->
                assertThat(record.getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.APPLIED));
    }

    @Test
    void aNewReservationHoldKeepsWorkPending() {
        Long id = enqueueWithoutImmediateApply(fixture(), SeatStatus.AVAILABLE);
        when(redis.execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString())).thenReturn(0L);
        publisher.publish(id);
        SeatCacheSyncOutbox pending = outbox.findById(id).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.PENDING);
        assertThat(pending.getLastErrorType()).isEqualTo("OCCUPIED_BY_ANOTHER_RESERVATION");
        assertThat(pending.getRetryCount()).isEqualTo(1);
    }

    @Test
    void lostRedisResponseKeepsWorkForSafeRedelivery() {
        Long id = enqueueWithoutImmediateApply(fixture(), SeatStatus.RESERVED);
        when(redis.execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("response lost after Redis applied"));
        publisher.publish(id);
        makeDue(id);
        doReturn(1L).when(redis).execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString());
        publisher.publish(id);
        publisher.publish(id);
        assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.APPLIED);
        verify(redis, times(2)).execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void appliedStatusCommitFailureLeavesWorkForAnotherDelivery() {
        Long id = enqueueWithoutImmediateApply(fixture(), SeatStatus.RESERVED);
        when(redis.execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void beforeCommit(boolean readOnly) {
                            throw new IllegalStateException("commit failed after Redis applied");
                        }
                    });
                    return 1L;
                });
        doCallRealMethod().when(dispatcher).publishSafely(anyLong());
        dispatcher.publishSafely(id);
        assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.PENDING);
        doReturn(1L).when(redis).execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString());
        dispatcher.publishSafely(id);
        assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.APPLIED);
        verify(redis, times(2)).execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void concurrentWorkersApplyOneOutboxOnlyOnce() throws Exception {
        Long id = enqueueWithoutImmediateApply(fixture(), SeatStatus.RESERVED);
        CountDownLatch redisEntered = new CountDownLatch(1);
        CountDownLatch finishRedis = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        when(redis.execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    redisEntered.countDown();
                    if (!finishRedis.await(10, TimeUnit.SECONDS)) throw new AssertionError("Redis wait timed out");
                    return 1L;
                });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> publisher.publish(id));
            assertThat(redisEntered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> { secondStarted.countDown(); publisher.publish(id); });
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            finishRedis.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            verify(redis, times(1)).execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString());
            assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(SeatCacheSyncOutboxStatus.APPLIED);
        } finally {
            finishRedis.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void seatLockRemainsHeldUntilRedisApplicationCompletes() throws Exception {
        Long seatId = fixture();
        Long id = enqueueWithoutImmediateApply(seatId, SeatStatus.RESERVED);
        CountDownLatch redisEntered = new CountDownLatch(1);
        CountDownLatch finishRedis = new CountDownLatch(1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        when(redis.execute(eq(SeatCacheSyncScripts.APPLY), anyList(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    redisEntered.countDown();
                    if (!finishRedis.await(10, TimeUnit.SECONDS)) throw new AssertionError("Redis wait timed out");
                    return 1L;
                });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var applying = executor.submit(() -> publisher.publish(id));
            assertThat(redisEntered.await(10, TimeUnit.SECONDS)).isTrue();
            var writer = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                writerStarted.countDown();
                Seat seat = seats.findByIdForUpdate(seatId).orElseThrow();
                seat.available();
                service.enqueue(List.of(seat), "cancel", "user", "order");
            }));
            assertThat(writerStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            finishRedis.countDown();
            applying.get(10, TimeUnit.SECONDS);
            writer.get(10, TimeUnit.SECONDS);
            assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(outbox.count()).isEqualTo(2);
        } finally {
            finishRedis.countDown();
            executor.shutdownNow();
        }
    }

    private Long fixture() {
        return new TransactionTemplate(transactions).execute(tx -> {
            Event event = events.save(Event.builder().artistName("가수").title("공연").venue("공연장")
                    .eventDateTime(LocalDateTime.now().plusDays(1)).totalSeats(1).availableSeats(1)
                    .status(EventStatus.ON_SALE).maxTicketsPerPerson(4).build());
            return seats.save(Seat.builder().event(event).zone("VIP").seatRow(1).seatCol(1)
                    .grade(SeatGrade.VIP).price(10000).status(SeatStatus.AVAILABLE).build()).getSeatId();
        });
    }

    private Long enqueueWithoutImmediateApply(Long seatId, SeatStatus target) {
        doNothing().when(dispatcher).publishSafely(anyLong());
        return new TransactionTemplate(transactions).execute(tx -> {
            Seat seat = seats.findByIdForUpdate(seatId).orElseThrow();
            if (target == SeatStatus.RESERVED) seat.reserved();
            else if (target == SeatStatus.LOCKED) seat.lock();
            else seat.available();
            service.enqueue(List.of(seat), "sync", "user", "order");
            return outbox.findAll(Sort.by("outboxId")).getLast().getOutboxId();
        });
    }

    private void makeDue(Long id) {
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                entityManager.createQuery("update SeatCacheSyncOutbox o set o.nextAttemptAt = :now where o.outboxId = :id")
                        .setParameter("now", LocalDateTime.now().minusSeconds(1)).setParameter("id", id).executeUpdate());
    }
}
