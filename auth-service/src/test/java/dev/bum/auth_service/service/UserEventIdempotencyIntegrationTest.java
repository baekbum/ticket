package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.RedisException;
import dev.bum.auth_service.exception.UserNotExistException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthJpaRepository;
import dev.bum.auth_service.jpa.AuthRepositoryImpl;
import dev.bum.auth_service.jpa.UserLoginLockJpaRepository;
import dev.bum.auth_service.jpa.event.ProcessedUserEventJpaRepository;
import dev.bum.auth_service.kafka.UserEventConsumer;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.user.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:user_event_idempotency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AuthService.class, AuthRepositoryImpl.class, UserEventIdempotencyService.class, UserEventConsumer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserEventIdempotencyIntegrationTest {
    @Autowired UserEventConsumer consumer;
    @Autowired AuthService service;
    @Autowired AuthJpaRepository accounts;
    @Autowired ProcessedUserEventJpaRepository receipts;
    @Autowired UserLoginLockJpaRepository loginLocks;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AuthRepositoryImpl repository;
    @MockitoBean LoginAttemptService loginAttempts;
    @MockitoBean JwtTokenProvider tokens;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean TokenStateStore states;

    @BeforeEach
    void configure() {
        receipts.deleteAll();
        loginLocks.deleteAll();
        accounts.deleteAll();
        accounts.saveAndFlush(Auth.builder().id(1L).userId("member")
                .password("hash-before").role(UserRole.ROLE_USER).build());
    }

    @Test
    void duplicateUpdateDoesNotReapplyAfterAnotherUpdate() {
        UserDtoForEvent first = event("update-1", TopicEventType.UPDATE, "ROLE_ADMIN");
        consumer.consume(first);
        consumer.consume(event("update-2", TopicEventType.UPDATE, "ROLE_USER"));
        long latestVersion = account().getTokenVersion();
        consumer.consume(first);

        assertThat(account().getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(account().getTokenVersion()).isEqualTo(latestVersion);
        assertThat(receipts.count()).isEqualTo(2);
        verify(repository, times(2)).update(any());
    }

    @Test
    void duplicateCreateDoesNotRestoreInitialPasswordOrTokenVersion() {
        accounts.deleteAll();
        UserDtoForEvent created = event("create-1", TopicEventType.CREATE, "ROLE_USER");
        consumer.consume(created);
        UserDtoForEvent updated = event("update-1", TopicEventType.UPDATE, "ROLE_ADMIN");
        updated.setPassword("hash-after");
        consumer.consume(updated);
        long version = account().getTokenVersion();
        consumer.consume(created);

        assertThat(account().getPassword()).isEqualTo("hash-after");
        assertThat(account().getRole()).isEqualTo(UserRole.ROLE_ADMIN);
        assertThat(account().getTokenVersion()).isEqualTo(version);
        verify(repository, times(1)).insert(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "role", "userId", "eventType"})
    void sameKeyWithDifferentRequestIsRejected(String changedField) {
        UserDtoForEvent event = event("same-key", TopicEventType.UPDATE, "ROLE_ADMIN");
        consumer.consume(event);
        switch (changedField) {
            case "password" -> event.setPassword("another-hash");
            case "role" -> event.setRole("ROLE_USER");
            case "userId" -> event.setUserId("other");
            case "eventType" -> event.setEventType(TopicEventType.CREATE);
        }

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("같은 회원의 멱등키로 다른 요청 내용을 처리할 수 없습니다.");
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(account().getRole()).isEqualTo(UserRole.ROLE_ADMIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "UPDATE"})
    void differentAccountsCanUseTheSameKeyAndRetryIndependently(String eventType) {
        TopicEventType type = TopicEventType.valueOf(eventType);
        if (type == TopicEventType.CREATE) {
            accounts.deleteAll();
        } else {
            accounts.saveAndFlush(Auth.builder().id(2L).userId("other")
                    .password("hash-before").role(UserRole.ROLE_USER).build());
        }
        UserDtoForEvent first = event("shared-key", type, "ROLE_ADMIN");
        UserDtoForEvent second = event("shared-key", type, "ROLE_ADMIN");
        second.setId(2L);
        second.setUserId("other");

        consumer.consume(first);
        consumer.consume(second);
        long firstVersion = account().getTokenVersion();
        long secondVersion = accounts.findById(2L).orElseThrow().getTokenVersion();
        consumer.consume(first);
        consumer.consume(second);

        assertThat(receipts.findAll()).hasSize(2)
                .extracting(receipt -> receipt.getAuthId()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(account().getRole()).isEqualTo(UserRole.ROLE_ADMIN);
        assertThat(account().getTokenVersion()).isEqualTo(firstVersion);
        assertThat(accounts.findById(2L).orElseThrow().getRole()).isEqualTo(UserRole.ROLE_ADMIN);
        assertThat(accounts.findById(2L).orElseThrow().getTokenVersion()).isEqualTo(secondVersion);
        if (type == TopicEventType.CREATE) {
            verify(repository, times(2)).insert(any());
        } else {
            verify(repository, times(2)).update(any());
        }
    }

    @Test
    void dbFailureRollsBackReceiptAndAllowsTheSameKeyToRetry() {
        UserDtoForEvent event = event("retry-db", TopicEventType.UPDATE, "ROLE_ADMIN");
        event.setId(2L);
        event.setUserId("missing");
        assertThatThrownBy(() -> consumer.consume(event)).isInstanceOf(UserNotExistException.class);
        assertThat(receipts.count()).isZero();

        accounts.saveAndFlush(Auth.builder().id(2L).userId("missing")
                .password("hash-before").role(UserRole.ROLE_USER).build());
        consumer.consume(event);
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(accounts.findById(2L).orElseThrow().getRole()).isEqualTo(UserRole.ROLE_ADMIN);
    }

    @Test
    void commitFailureRollsBackAccountAndReceiptTogether() {
        UserDtoForEvent event = event("retry-commit", TopicEventType.UPDATE, "ROLE_ADMIN");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            consumer.consume(event);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("커밋 실패");
                }
            });
        })).isInstanceOf(IllegalStateException.class).hasMessage("커밋 실패");
        assertThat(receipts.count()).isZero();
        assertThat(account().getRole()).isEqualTo(UserRole.ROLE_USER);
        verifyNoInteractions(states);

        consumer.consume(event);
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(account().getRole()).isEqualTo(UserRole.ROLE_ADMIN);
    }

    @Test
    void redisFailureRetriesCurrentStateWithoutRepeatingDbUpdate() {
        doThrow(new IllegalStateException("Redis 장애")).doNothing().when(states).publish(eq("member"), any());
        UserDtoForEvent event = event("retry-redis", TopicEventType.UPDATE, "ROLE_ADMIN");
        assertThatThrownBy(() -> consumer.consume(event)).isInstanceOf(RedisException.class);
        assertThat(receipts.count()).isEqualTo(1);
        long version = account().getTokenVersion();

        consumer.consume(event);
        verify(repository, times(1)).update(any());
        verify(states, times(2)).publish(eq("member"), any());
        assertThat(account().getTokenVersion()).isEqualTo(version);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentSameKeyWaitsForCommitOrRetriesAfterRollback(boolean rollbackFirst) throws Exception {
        UserDtoForEvent event = event("concurrent-key", TopicEventType.UPDATE, "ROLE_ADMIN");
        CountDownLatch updating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (attempts.incrementAndGet() == 1) {
                updating.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("대기 시간 초과");
                if (rollbackFirst) throw new IllegalStateException("첫 요청 실패");
            }
            return result;
        }).when(repository).update(any());

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> consumer.consume(event));
            assertThat(updating.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> consumer.consume(event));
            try {
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            if (rollbackFirst) {
                assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
            } else {
                first.get(10, TimeUnit.SECONDS);
            }
            second.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(account().getRole()).isEqualTo(UserRole.ROLE_ADMIN);
        assertThat(account().getTokenVersion()).isEqualTo(2);
        verify(repository, times(rollbackFirst ? 2 : 1)).update(any());
    }

    @Test
    void duplicateCreateDoesNotResurrectDeletedAccount() {
        UserDtoForEvent created = event("create-delete", TopicEventType.CREATE, "ROLE_USER");
        consumer.consume(created);
        service.deleteUserTopic(event("delete", TopicEventType.DELETE, null));
        consumer.consume(created);
        assertThat(accounts.findById(1L)).isEmpty();
        assertThat(receipts.count()).isEqualTo(1);
        verify(repository, times(1)).insert(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void eventWithoutKeyIsRejected(String key) {
        assertThatThrownBy(() -> consumer.consume(event(key, TopicEventType.UPDATE, "ROLE_ADMIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("회원 이벤트의 멱등키 또는 대상 정보가 올바르지 않습니다.");
        assertThat(account().getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(receipts.count()).isZero();
        verify(repository, never()).update(any());
        verifyNoInteractions(states);
    }

    private Auth account() {
        return accounts.findById(1L).orElseThrow();
    }

    private UserDtoForEvent event(String key, TopicEventType type, String role) {
        return UserDtoForEvent.builder().idempotencyKey(key).eventType(type).id(1L).userId("member")
                .password("hash-before").role(role).build();
    }
}
