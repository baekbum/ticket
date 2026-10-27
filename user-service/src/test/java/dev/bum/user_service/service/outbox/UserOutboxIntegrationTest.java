package dev.bum.user_service.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.dto.FindPasswordRequest;
import dev.bum.common.service.user.user.dto.ResetPasswordRequest;
import dev.bum.user_service.jpa.outbox.UserOutboxEvent;
import dev.bum.user_service.jpa.outbox.UserOutboxEventJpaRepository;
import dev.bum.user_service.jpa.outbox.UserOutboxStatus;
import dev.bum.user_service.jpa.user.UserJpaRepository;
import dev.bum.user_service.jpa.user.UserRepositoryImpl;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserKafkaEventState;
import dev.bum.user_service.jpa.user.UserKafkaEventStateJpaRepository;
import dev.bum.user_service.service.user.UserKafkaEventStateService;
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
import java.util.Comparator;
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
@Import({UserService.class, UserRepositoryImpl.class, UserOutboxService.class, UserKafkaEventStateService.class,
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
    @Autowired UserKafkaEventStateJpaRepository eventStates;
    @MockitoSpyBean UserOutboxEventJpaRepository outbox;
    @MockitoSpyBean UserOutboxPublisher publisher;
    @MockitoSpyBean UserRepositoryImpl userRepository;
    @Autowired UserOutboxDispatcher dispatcher;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean JPAQueryFactory queryFactory;
    @MockitoBean PasswordEncoder passwords;
    @MockitoBean KafkaTemplate<String, UserDtoForEvent> kafka;

    @BeforeEach
    void configure() {
        outbox.deleteAll();
        eventStates.deleteAll();
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
        assertThat(eventStates.count()).isZero();
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
        assertThat(sent.getAllValues().getFirst().getIdempotencyKey()).isNotBlank();
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

    @Test
    void newOperationsUseDifferentKeys() {
        users.insert(signup("member"));
        users.update("member", UpdateUserRequest.builder().role("ROLE_ADMIN").build());
        ArgumentCaptor<UserDtoForEvent> fresh = ArgumentCaptor.forClass(UserDtoForEvent.class);
        verify(kafka, times(2)).send(eq("user-event"), eq("member"), fresh.capture());
        assertThat(fresh.getAllValues()).extracting(UserDtoForEvent::getIdempotencyKey).doesNotHaveDuplicates().doesNotContainNull();
    }

    @Test
    void outboxWithoutKeyIsNotPublished() {
        UserOutboxEvent invalid = outbox.saveAndFlush(UserOutboxEvent.builder()
                .userId("member").topic("user-event").nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                .payload("{\"eventType\":\"UPDATE\",\"id\":1,\"userId\":\"member\",\"role\":\"ROLE_ADMIN\"}")
                .build());
        publisher.publish(invalid.getOutboxId());
        verify(kafka, never()).send(anyString(), anyString(), any(UserDtoForEvent.class));
        assertThat(outbox.findById(invalid.getOutboxId()).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(UserOutboxStatus.PENDING);
            assertThat(event.getLastError()).isEqualTo("IllegalArgumentException");
        });
    }

    @Test
    void versionsAndFullSnapshotsSurviveRetryAndDelete() {
        users.insert(signup("member"));
        when(passwords.encode("changed-password")).thenReturn("changed-hash");
        users.update("member", UpdateUserRequest.builder().password("changed-password").build());
        users.update("member", UpdateUserRequest.builder().role("ROLE_ADMIN").build());
        users.delete("member");
        ArgumentCaptor<UserDtoForEvent> sent = ArgumentCaptor.forClass(UserDtoForEvent.class);
        verify(kafka, times(4)).send(eq("user-event"), eq("member"), sent.capture());
        assertThat(sent.getAllValues()).extracting(UserDtoForEvent::getEventVersion).containsExactly(1L, 2L, 3L, 4L);
        assertThat(sent.getAllValues().get(2).getPassword()).isEqualTo("changed-hash");
        assertThat(sent.getAllValues().get(2).getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(sent.getAllValues().get(3).getEventType()).isEqualTo(TopicEventType.DELETE);
        assertThat(eventStates.count()).isZero();
    }

    @Test
    void rollbackDoesNotAdvanceSourceVersion() {
        users.insert(signup("member"));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            users.update("member", UpdateUserRequest.builder().role("ROLE_ADMIN").build());
            tx.setRollbackOnly();
        });
        Long userPk = userRows.findByUserId("member").orElseThrow().getId();
        assertThat(eventStates.findById(userPk).orElseThrow().getEventVersion()).isEqualTo(1L);
        users.update("member", UpdateUserRequest.builder().role("ROLE_ADMIN").build());
        ArgumentCaptor<UserDtoForEvent> sent = ArgumentCaptor.forClass(UserDtoForEvent.class);
        verify(kafka, times(2)).send(eq("user-event"), eq("member"), sent.capture());
        assertThat(sent.getAllValues()).extracting(UserDtoForEvent::getEventVersion).containsExactly(1L, 2L);
    }

    @Test
    void passwordResetVersionIncrementsIndependentlyAndPersistsInLaterSnapshots() {
        users.insert(signup("member"));
        var reset = users.findPasswordByPhoneNumber(FindPasswordRequest.builder()
                .userId("member").name("회원").phoneNumber("01012345678").build());
        users.resetPassword(ResetPasswordRequest.builder().resetToken(reset.getResetToken()).password("new-password").build());
        users.update("member", UpdateUserRequest.builder().password("ordinary-change").build());
        var secondReset = users.findPasswordByPhoneNumber(FindPasswordRequest.builder()
                .userId("member").name("회원").phoneNumber("01012345678").build());
        users.resetPassword(ResetPasswordRequest.builder()
                .resetToken(secondReset.getResetToken()).password("second-password").build());
        users.update("member", UpdateUserRequest.builder().password("following-change").build());
        ArgumentCaptor<UserDtoForEvent> sent = ArgumentCaptor.forClass(UserDtoForEvent.class);
        verify(kafka, times(5)).send(eq("user-event"), eq("member"), sent.capture());
        assertThat(sent.getAllValues()).extracting(UserDtoForEvent::getPasswordResetVersion)
                .containsExactly(0L, 1L, 1L, 2L, 2L);
        assertThat(sent.getAllValues()).extracting(UserDtoForEvent::getEventVersion)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(sent.getAllValues().get(4).getPasswordReset()).isFalse();
        Long userPk = userRows.findByUserId("member").orElseThrow().getId();
        assertThat(eventStates.findById(userPk).orElseThrow().getPasswordResetVersion()).isEqualTo(2L);
    }

    @Test
    void concurrentUpdatesUseOrderedVersionsAndLatestFullState() throws Exception {
        users.insert(signup("member"));
        when(passwords.encode("changed-password")).thenReturn("changed-hash");
        CountDownLatch changing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            User result = (User) invocation.callRealMethod();
            if (changing.getCount() > 0) {
                changing.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("대기 시간 초과");
            }
            return result;
        }).when(userRepository).update(eq("member"), any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> users.update("member", UpdateUserRequest.builder().password("changed-password").build()));
            assertThat(changing.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> users.update("member", UpdateUserRequest.builder().role("ROLE_ADMIN").build()));
            try {
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        var member = userRows.findByUserId("member").orElseThrow();
        dispatcher.retryPendingEvents();
        assertThat(eventStates.findById(member.getId()).orElseThrow().getEventVersion()).isEqualTo(3L);
        assertThat(member.getPassword()).isEqualTo("changed-hash");
        ArgumentCaptor<UserDtoForEvent> sent = ArgumentCaptor.forClass(UserDtoForEvent.class);
        verify(kafka, times(3)).send(eq("user-event"), eq("member"), sent.capture());
        UserDtoForEvent latest = sent.getAllValues().stream().max(Comparator.comparing(UserDtoForEvent::getEventVersion)).orElseThrow();
        assertThat(latest.getPassword()).isEqualTo("changed-hash");
        assertThat(latest.getRole()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void stateSharesUserPrimaryKeyAndIsDeletedAfterFinalEvent() {
        users.insert(signup("member"));
        Long userPk = userRows.findByUserId("member").orElseThrow().getId();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            UserKafkaEventState state = eventStates.findById(userPk).orElseThrow();
            assertThat(state.getUserPk()).isEqualTo(state.getUser().getId()).isEqualTo(userPk);
            assertThat(state.getEventVersion()).isEqualTo(1L);
        });
        users.delete("member");
        assertThat(userRows.existsById(userPk)).isFalse();
        assertThat(eventStates.existsById(userPk)).isFalse();
        ArgumentCaptor<UserDtoForEvent> sent = ArgumentCaptor.forClass(UserDtoForEvent.class);
        verify(kafka, times(2)).send(eq("user-event"), eq("member"), sent.capture());
        assertThat(sent.getAllValues().get(1).getEventType()).isEqualTo(TopicEventType.DELETE);
        assertThat(sent.getAllValues().get(1).getEventVersion()).isEqualTo(2L);
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
