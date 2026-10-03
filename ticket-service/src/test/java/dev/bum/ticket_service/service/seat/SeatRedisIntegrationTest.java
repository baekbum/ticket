package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailure;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureStatus;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

/** TICKET_REDIS_INTEGRATION=true일 때 실행하며, 이 테스트에서 생성한 키와 ACL 사용자만 삭제한다. */
@EnabledIfEnvironmentVariable(named = "TICKET_REDIS_INTEGRATION", matches = "true")
class SeatRedisIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private final List<LettuceConnectionFactory> restrictedFactories = new ArrayList<>();
    private final Set<String> testKeys = new HashSet<>();
    private final List<String> aclUsers = new ArrayList<>();
    private SeatRepository seats;
    private EventRepository events;
    private TicketRepository tickets;
    private SeatCacheSyncFailureService failures;
    private Event event;

    @BeforeEach
    void setUp() {
        factory = connectionFactory(null, null);
        redis = new StringRedisTemplate(factory);
        assertThat(redis.execute((RedisCallback<String>) connection -> connection.ping())).isEqualTo("PONG");
        seats = mock(SeatRepository.class);
        events = mock(EventRepository.class);
        tickets = mock(TicketRepository.class);
        failures = mock(SeatCacheSyncFailureService.class);
        long id = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_000_000_000_000L);
        event = Event.builder().eventId(id).ticketLimitScope(TicketLimitScope.PER_EVENT)
                .maxTicketsPerPerson(4).saleStartAt(LocalDateTime.now().minusHours(1))
                .eventDateTime(LocalDateTime.now().plusDays(1)).runningMinutes(120).build();
        given(events.selectById(id)).willReturn(event);
    }

    @AfterEach
    void cleanUp() {
        restrictedFactories.forEach(LettuceConnectionFactory::destroy);
        if (redis != null) {
            if (!testKeys.isEmpty()) redis.delete(testKeys);
            for (String user : aclUsers) {
                redis.execute((RedisCallback<Long>) connection -> {
                    try {
                        RedisAsyncCommands<?, ?> commands = (RedisAsyncCommands<?, ?>) connection.getNativeConnection();
                        return commands.aclDeluser(user).get(3, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException("테스트 ACL 사용자 정리 실패: " + user, e);
                    }
                });
            }
        }
        if (factory != null) factory.destroy();
    }

    @Test
    void concurrent_occupation_has_exactly_one_winner() throws Exception {
        SeatCacheService service = service(redis);
        String key = key(1);
        redis.opsForValue().set(key, "AVAILABLE", Duration.ofMinutes(2));
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        try {
            List<Future<?>> requests = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                String user = "integration-user-" + index;
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                        service.occupySeat(SeatOccupyRequest.builder().eventId(event.getEventId()).userId(user)
                                .seats(List.of(SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build())).build());
                        winners.incrementAndGet();
                    } catch (dev.bum.ticket_service.exception.seat.SeatAlreadyOccupiedException expected) {
                        // 경쟁에서 실패한 요청은 성공한 요청의 잠금을 삭제하지 않고 거절되어야 한다.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> request : requests) request.get(15, TimeUnit.SECONDS);
            assertThat(winners.get()).isEqualTo(1);
            assertThat(redis.opsForValue().get(key)).startsWith("LOCKED:")
                    .isEqualTo(redis.opsForValue().get(key + ":lock"));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void warm_up_only_fills_database_available_seats_without_existing_redis_state() {
        String available = key(1);
        String reserved = key(2);
        String existing = key(3);
        redis.opsForValue().set(existing, "RESERVED", Duration.ofMinutes(2));
        given(seats.selectByEventId(event.getEventId())).willReturn(List.of(
                seat(1, SeatStatus.AVAILABLE), seat(2, SeatStatus.RESERVED), seat(3, SeatStatus.AVAILABLE)));

        assertThat(service(redis)
                .warmUpEventSeatsToCache(event.getEventId(), SeatCacheWarmUpMode.MISSING_ONLY)).contains("반영 1개");

        assertThat(redis.opsForValue().get(available)).isEqualTo("AVAILABLE");
        assertThat(redis.getExpire(available, TimeUnit.SECONDS)).isBetween(604_790L, 604_800L);
        assertThat(redis.hasKey(reserved)).isFalse();
        assertThat(redis.opsForValue().get(existing)).isEqualTo("RESERVED");
    }

    @Test
    void warm_up_and_retry_preserve_a_new_users_lock() {
        String key = key(1);
        redis.opsForValue().set(key + ":lock", "LOCKED:new-user:new-order", Duration.ofMinutes(2));
        given(seats.selectByEventId(event.getEventId())).willReturn(List.of(seat(1, SeatStatus.AVAILABLE)));
        assertThat(service(redis)
                .warmUpEventSeatsToCache(event.getEventId(), SeatCacheWarmUpMode.MISSING_ONLY)).contains("반영 0개");
        assertThat(redis.hasKey(key)).isFalse();

        SeatCacheSyncFailureJpaRepository history = mock(SeatCacheSyncFailureJpaRepository.class);
        SeatJpaRepository currentSeats = mock(SeatJpaRepository.class);
        SeatCacheSyncFailure failure = failure(key, "AVAILABLE");
        given(history.findByIdForUpdate(1L)).willReturn(Optional.of(failure));
        given(currentSeats.findByCacheCoordinatesForUpdate(event.getEventId(), "VIP", 1, 1))
                .willReturn(List.of(seat(1, SeatStatus.AVAILABLE)));
        SeatCacheSyncFailureService retry = new SeatCacheSyncFailureService(history, redis, currentSeats);

        assertThatThrownBy(() -> retry.retry(1L)).isInstanceOf(IllegalStateException.class);
        assertThat(failure.getStatus()).isEqualTo(SeatCacheSyncFailureStatus.PENDING);
        assertThat(redis.opsForValue().get(key + ":lock")).isEqualTo("LOCKED:new-user:new-order");
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void retry_uses_current_database_status_and_event_ttl() {
        String key = key(1);
        redis.opsForValue().set(key, "RESERVED", Duration.ofMinutes(2));
        SeatCacheSyncFailureJpaRepository history = mock(SeatCacheSyncFailureJpaRepository.class);
        SeatJpaRepository currentSeats = mock(SeatJpaRepository.class);
        SeatCacheSyncFailure failure = failure(key, "RESERVED");
        given(history.findByIdForUpdate(1L)).willReturn(Optional.of(failure));
        given(currentSeats.findByCacheCoordinatesForUpdate(event.getEventId(), "VIP", 1, 1))
                .willReturn(List.of(seat(1, SeatStatus.AVAILABLE)));

        new SeatCacheSyncFailureService(history, redis, currentSeats).retry(1L);

        assertThat(redis.opsForValue().get(key)).isEqualTo("AVAILABLE");
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(86_400L, 93_600L);
        assertThat(failure.getStatus()).isEqualTo(SeatCacheSyncFailureStatus.RESOLVED);
    }

    @Test
    void real_redis_acl_errors_record_only_failed_seats_and_continue() {
        List<Seat> targets = List.of(seat(1, SeatStatus.AVAILABLE), seat(2, SeatStatus.AVAILABLE),
                seat(3, SeatStatus.AVAILABLE), seat(4, SeatStatus.AVAILABLE));
        for (int col = 1; col <= 4; col++) {
            redis.opsForValue().set(key(col), "RESERVED", Duration.ofMinutes(2));
        }
        StringRedisTemplate restricted = restrictedRedis(List.of(key(1), key(1) + ":lock", key(4), key(4) + ":lock"));

        service(restricted).syncAvailableSeatsAfterCommit(targets);

        assertThat(redis.opsForValue().get(key(1))).isEqualTo("AVAILABLE");
        assertThat(redis.opsForValue().get(key(4))).isEqualTo("AVAILABLE");
        assertThat(redis.opsForValue().get(key(2))).isEqualTo("RESERVED");
        assertThat(redis.opsForValue().get(key(3))).isEqualTo("RESERVED");
        then(failures).should().recordFailure(eq("syncAvailableSeatsAfterCommit"), anyString(),
                eq(List.of(key(2))), eq(List.of("AVAILABLE")), any(Exception.class));
        then(failures).should().recordFailure(eq("syncAvailableSeatsAfterCommit"), anyString(),
                eq(List.of(key(3))), eq(List.of("AVAILABLE")), any(Exception.class));
        then(failures).shouldHaveNoMoreInteractions();
    }

    @Test
    void seat_cache_is_updated_only_after_database_commit() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table transaction_marker (amount integer not null)");
        jdbc.update("insert into transaction_marker values (0)");
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        SeatCacheService service = service(redis);
        String seatKey = key(1);
        try {
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("update transaction_marker set amount = 1");
                service.syncReservedSeatsAfterCommit(List.of(seat(1, SeatStatus.RESERVED)));
                throw new IllegalStateException("DB 롤백");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("select amount from transaction_marker", Integer.class)).isZero();
            assertThat(redis.hasKey(seatKey)).isFalse();

            transaction.executeWithoutResult(status -> {
                jdbc.update("update transaction_marker set amount = 1");
                service.syncReservedSeatsAfterCommit(List.of(seat(1, SeatStatus.RESERVED)));
                assertThat(redis.hasKey(seatKey)).isFalse();
            });
            assertThat(jdbc.queryForObject("select amount from transaction_marker", Integer.class)).isEqualTo(1);
            assertThat(redis.opsForValue().get(seatKey)).isEqualTo("RESERVED");
        } finally {
            jdbc.execute("shutdown");
        }
    }

    private SeatCacheService service(StringRedisTemplate template) {
        return new SeatCacheService(seats, events, template, failures);
    }
    private Seat seat(int col, SeatStatus status) {
        return Seat.builder().seatId((long) col).event(event).zone("VIP").seatRow(1).seatCol(col).status(status).build();
    }

    private String key(int col) {
        String key = "event:" + event.getEventId() + ":seat:VIP:1:" + col;
        testKeys.add(key);
        testKeys.add(key + ":lock");
        return key;
    }

    private SeatCacheSyncFailure failure(String key, String value) {
        return SeatCacheSyncFailure.builder().operation("integration-test").keyPrefix("event:{eventId}:seat")
                .redisKeys(key).targetValue(value).failureMessage("test failure").build();
    }

    private StringRedisTemplate restrictedRedis(List<String> allowedKeys) {
        String user = "ticket-integration-" + UUID.randomUUID();
        String password = UUID.randomUUID().toString();
        aclUsers.add(user);
        List<String> arguments = new ArrayList<>(List.of("SETUSER", user, "on", ">" + password, "resetkeys",
                "+@connection", "+@scripting", "+get", "+exists", "+psetex", "+del"));
        allowedKeys.forEach(key -> arguments.add("~" + key));
        acl(arguments.toArray(String[]::new));
        LettuceConnectionFactory restricted = connectionFactory(user, password);
        restrictedFactories.add(restricted);
        return new StringRedisTemplate(restricted);
    }

    private void acl(String... arguments) {
        byte[][] bytes = Arrays.stream(arguments).map(value -> value.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
        redis.execute((RedisCallback<Object>) connection -> connection.execute("ACL", bytes));
    }

    private LettuceConnectionFactory connectionFactory(String user, String password) {
        return connectionFactory(user, password,
                Integer.parseInt(System.getenv().getOrDefault("TICKET_TEST_REDIS_PORT", "6390")));
    }

    private LettuceConnectionFactory connectionFactory(String user, String password, int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("TICKET_TEST_REDIS_HOST", "127.0.0.1"),
                port);
        config.setDatabase(Integer.parseInt(System.getenv().getOrDefault("TICKET_TEST_REDIS_DATABASE", "15")));
        if (user != null) {
            config.setUsername(user);
            config.setPassword(password);
        }
        LettuceConnectionFactory connection = new LettuceConnectionFactory(config,
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(3)).shutdownTimeout(Duration.ZERO).build());
        connection.afterPropertiesSet();
        return connection;
    }
}
