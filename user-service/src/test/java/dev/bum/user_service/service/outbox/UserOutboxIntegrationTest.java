package dev.bum.user_service.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.user_service.jpa.outbox.UserOutboxEvent;
import dev.bum.user_service.jpa.outbox.UserOutboxEventJpaRepository;
import dev.bum.user_service.jpa.outbox.UserOutboxStatus;
import dev.bum.user_service.jpa.user.UserJpaRepository;
import dev.bum.user_service.jpa.user.UserRepositoryImpl;
import dev.bum.user_service.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"topic.user.name=user-event", "app.user-outbox.retry-delay-ms=0"})
@ActiveProfiles("test")
@Import({UserService.class, UserRepositoryImpl.class, UserOutboxService.class,
        UserOutboxPublisher.class, UserOutboxDispatcher.class, UserOutboxIntegrationTest.JsonConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserOutboxIntegrationTest {
    @TestConfiguration
    static class JsonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Autowired UserService users;
    @Autowired UserJpaRepository userRows;
    @MockitoSpyBean UserOutboxEventJpaRepository outbox;
    @MockitoSpyBean UserOutboxPublisher publisher;
    @Autowired UserOutboxDispatcher dispatcher;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean JPAQueryFactory queryFactory;
    @MockitoBean PasswordEncoder passwords;
    @MockitoBean KafkaTemplate<String, UserDtoForEvent> kafka;

    @BeforeEach
    void configure() {
        outbox.deleteAll();
        userRows.deleteAll();
        when(passwords.encode(any())).thenReturn("encoded-password");
        when(kafka.send(anyString(), anyString(), any(UserDtoForEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void publishRunsOnlyAfterUserAndOutboxAreCommitted() {
        when(kafka.send(anyString(), anyString(), any(UserDtoForEvent.class))).thenAnswer(invocation -> {
            TransactionTemplate reader = new TransactionTemplate(transactionManager);
            reader.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
            reader.executeWithoutResult(status -> {
                assertThat(userRows.findByUserId("member")).isPresent();
                assertThat(outbox.findAll()).singleElement()
                        .satisfies(event -> assertThat(event.getStatus()).isEqualTo(UserOutboxStatus.PENDING));
            });
            return CompletableFuture.completedFuture(null);
        });
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.insert(signup("member"));
            verifyNoInteractions(kafka);
        });

        assertThat(outbox.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(UserOutboxStatus.PUBLISHED);
            assertThat(event.getAttemptCount()).isEqualTo(1);
            assertThat(event.getPublishedAt()).isNotNull();
        });
    }

    @Test
    void rollbackRemovesUserAndOutboxWithoutPublishing() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.insert(signup("member"));
            status.setRollbackOnly();
        });

        assertThat(userRows.count()).isZero();
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(kafka);
    }

    @Test
    void commitFailureDoesNotPublishOrLeaveOutbox() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.insert(signup("member"));
            failCommit("회원 커밋 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("회원 커밋 실패");

        assertThat(userRows.count()).isZero();
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(kafka);
    }

    @Test
    void outboxInsertFailureRollsBackUser() {
        doThrow(new IllegalStateException("Outbox 저장 실패")).when(outbox).save(any(UserOutboxEvent.class));

        assertThatThrownBy(() -> users.insert(signup("member")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Outbox 저장 실패");
        assertThat(userRows.count()).isZero();
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(kafka);
    }

    @Test
    void asyncSendFailureKeepsCommittedUserAndSchedulerRetriesSamePayload() {
        when(kafka.send(anyString(), anyString(), any(UserDtoForEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));

        users.insert(signup("member"));
        assertThat(userRows.findByUserId("member")).isPresent();
        assertThat(outbox.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(UserOutboxStatus.PENDING);
            assertThat(event.getAttemptCount()).isEqualTo(1);
            assertThat(event.getLastError()).isEqualTo("IllegalStateException");
        });

        dispatcher.retryPendingEvents();
        ArgumentCaptor<UserDtoForEvent> sent = ArgumentCaptor.forClass(UserDtoForEvent.class);
        verify(kafka, times(2)).send(eq("user-event"), eq("member"), sent.capture());
        assertThat(sent.getAllValues().get(0)).isEqualTo(sent.getAllValues().get(1));
        assertThat(outbox.findAll().getFirst().getStatus()).isEqualTo(UserOutboxStatus.PUBLISHED);
    }

    @Test
    void synchronousSendFailureRespectsRetryDelay() {
        ReflectionTestUtils.setField(publisher, "retryDelayMs", 60000L);
        try {
            when(kafka.send(anyString(), anyString(), any(UserDtoForEvent.class)))
                    .thenThrow(new IllegalStateException("synchronous failure"));
            users.insert(signup("member"));
            assertThat(userRows.findByUserId("member")).isPresent();
            assertThat(outbox.findAll().getFirst().getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(30));
            dispatcher.retryPendingEvents();
            verify(kafka, times(1)).send(eq("user-event"), eq("member"), any(UserDtoForEvent.class));
        } finally {
            ReflectionTestUtils.setField(publisher, "retryDelayMs", 0L);
        }
    }

    @Test
    void schedulerRecoversWhenImmediateCallbackCannotRun() {
        doThrow(new IllegalStateException("콜백 중단")).doCallRealMethod().when(publisher).publish(anyLong());
        users.insert(signup("member"));
        verifyNoInteractions(kafka);
        assertThat(outbox.findAll().getFirst().getStatus()).isEqualTo(UserOutboxStatus.PENDING);

        dispatcher.retryPendingEvents();
        verify(kafka).send(eq("user-event"), eq("member"), any(UserDtoForEvent.class));
        assertThat(outbox.findAll().getFirst().getStatus()).isEqualTo(UserOutboxStatus.PUBLISHED);
    }

    @Test
    void brokerSuccessFollowedByStatusCommitFailureCanBeRedelivered() {
        when(kafka.send(anyString(), anyString(), any(UserDtoForEvent.class))).thenAnswer(invocation -> {
            failCommit("발행 완료 저장 실패");
            return CompletableFuture.completedFuture(null);
        }).thenReturn(CompletableFuture.completedFuture(null));

        users.insert(signup("member"));
        assertThat(userRows.findByUserId("member")).isPresent();
        assertThat(outbox.findAll().getFirst().getStatus()).isEqualTo(UserOutboxStatus.PENDING);
        dispatcher.retryPendingEvents();
        verify(kafka, times(2)).send(eq("user-event"), eq("member"), any(UserDtoForEvent.class));
        assertThat(outbox.findAll().getFirst().getStatus()).isEqualTo(UserOutboxStatus.PUBLISHED);
    }

    @Test
    void failedEarlierEventBlocksOnlyTheSameUser() {
        when(kafka.send(eq("user-event"), eq("member"), any(UserDtoForEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        users.insert(signup("member"));
        users.update("member", UpdateUserRequest.builder().role("ROLE_ADMIN").build());
        users.insert(signup("other"));
        verify(kafka, times(1)).send(eq("user-event"), eq("member"), any(UserDtoForEvent.class));
        verify(kafka, times(1)).send(eq("user-event"), eq("other"), any(UserDtoForEvent.class));

        when(kafka.send(eq("user-event"), eq("member"), any(UserDtoForEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        dispatcher.retryPendingEvents();
        dispatcher.retryPendingEvents();
        ArgumentCaptor<UserDtoForEvent> sent = ArgumentCaptor.forClass(UserDtoForEvent.class);
        verify(kafka, times(3)).send(eq("user-event"), eq("member"), sent.capture());
        assertThat(sent.getAllValues()).extracting(UserDtoForEvent::getRole)
                .containsExactly("ROLE_USER", "ROLE_USER", "ROLE_ADMIN");
        assertThat(outbox.findAll()).allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(UserOutboxStatus.PUBLISHED));
    }

    @Test
    void competingPublishersLockAndRecheckTheSameRow() throws Exception {
        doThrow(new IllegalStateException("콜백 중단")).when(publisher).publish(anyLong());
        users.insert(signup("member"));
        doCallRealMethod().when(publisher).publish(anyLong());
        Long id = outbox.findAll().getFirst().getOutboxId();
        CompletableFuture<SendResult<String, UserDtoForEvent>> acknowledgement = new CompletableFuture<>();
        CountDownLatch sending = new CountDownLatch(1);
        when(kafka.send(anyString(), anyString(), any(UserDtoForEvent.class))).thenAnswer(invocation -> {
            sending.countDown();
            return acknowledgement;
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> publisher.publish(id));
            assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> publisher.publish(id));
            try {
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                acknowledgement.complete(null);
            }
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            acknowledgement.complete(null);
        }
        verify(kafka).send(eq("user-event"), eq("member"), any(UserDtoForEvent.class));
        assertThat(outbox.findById(id).orElseThrow().getAttemptCount()).isEqualTo(1);
    }

    private void failCommit(String message) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                throw new IllegalStateException(message);
            }
        });
    }

    private InsertUserRequest signup(String id) {
        return InsertUserRequest.builder().userId(id).password("plaintext-password")
                .name("회원").phoneNumber(id.equals("member") ? "01012345678" : "01099999999")
                .email(id + "@test.com").build();
    }
}
