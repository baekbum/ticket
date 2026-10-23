package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import dev.bum.payment_gateway_service.service.refund.GatewayRefundProcessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@ContextConfiguration(classes = VirtualAccountDepositOutboxIntegrationTest.PersistenceConfig.class)
@Import({GatewayVirtualAccountService.class, VirtualAccountDepositOutboxService.class,
        VirtualAccountDepositOutboxDispatcher.class, VirtualAccountDepositOutboxPublisher.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VirtualAccountDepositOutboxIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.payment_gateway_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.payment_gateway_service.jpa")
    static class PersistenceConfig { }

    @Autowired GatewayVirtualAccountService gateway;
    @Autowired VirtualAccountDepositOutboxJpaRepository outbox;
    @Autowired VirtualAccountOutboxEventJpaRepository expirationOutbox;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired DummyVirtualAccountPaymentHistoryJpaRepository histories;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean VirtualAccountDepositOutboxDispatcher dispatcher;
    @MockitoBean TicketPaymentClient ticket;
    @MockitoBean GatewayRefundProcessService refunds;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String port = System.getenv("GATEWAY_DEPOSIT_TEST_POSTGRES_PORT");
        if (port != null && !port.isBlank()) {
            properties.add("spring.datasource.url", () ->
                    "jdbc:postgresql://127.0.0.1:" + Integer.parseInt(port) + "/gateway_deposit_test");
            properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            properties.add("spring.datasource.username", () -> "gateway_test");
            properties.add("spring.datasource.password", () -> "gateway_test");
        } else {
            properties.add("spring.datasource.url", () ->
                    "jdbc:h2:mem:gateway-deposit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            properties.add("spring.datasource.username", () -> "sa");
            properties.add("spring.datasource.password", () -> "");
        }
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setup() {
        outbox.deleteAll();
        expirationOutbox.deleteAll();
        histories.deleteAll();
        accounts.deleteAll();
        accounts.save(DummyVirtualAccount.issue("PAY-1", BankCompany.KB, "1111-123456",
                new BigDecimal("10000"), LocalDateTime.now().plusDays(1)
        ));
        when(ticket.completeVirtualAccountDeposit(any())).thenAnswer(invocation -> response());
    }

    @Test
    void repeatedIssueOfExpiredAccountCommitsOneExpirationNotification() {
        jdbc.update("update dummy_virtual_accounts set expires_at = ? where payment_no = ?",
                LocalDateTime.now().minusDays(1), "PAY-1"
        );
        GatewayVirtualAccountIssueRequest request = GatewayVirtualAccountIssueRequest.builder()
                .paymentNo("PAY-1").bankCompany(BankCompany.KB).amount(new BigDecimal("10000"))
                .eventDateTime(LocalDateTime.now().plusDays(2)).build();

        gateway.issue(request);
        gateway.issue(request);

        assertThat(accounts.findByPaymentNo("PAY-1").orElseThrow().getStatus())
                .isEqualTo(VirtualAccountPaymentStatus.EXPIRED);
        assertThat(histories.count()).isEqualTo(1);
        assertThat(histories.findAll().getFirst().getHistoryType()).isEqualTo(VirtualAccountPaymentHistoryType.EXPIRED);
        assertThat(expirationOutbox.count()).isEqualTo(1);
        assertThat(expirationOutbox.findAll().getFirst().getEventType()).isEqualTo(OutboxEventType.VIRTUAL_ACCOUNT_EXPIRED);
        verifyNoInteractions(ticket);
    }

    @Test
    void depositAndOutboxCommitBeforeHttpAndRepeatedDepositReusesResult() {
        when(ticket.completeVirtualAccountDeposit(any())).thenAnswer(invocation -> {
            // 독립 트랜잭션에서 조회하여 단순 flush가 아니라 커밋까지 끝났는지 확인한다.
            TransactionTemplate independent = new TransactionTemplate(transactions);
            independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independent.executeWithoutResult(status -> {
                assertThat(accounts.findByPaymentNo("PAY-1").orElseThrow().getStatus())
                        .isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
                assertThat(outbox.findByPaymentNo("PAY-1")).isPresent();
            });
            return response();
        });
        assertThat(gateway.deposit(request()).getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        gateway.deposit(request());
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(histories.count()).isEqualTo(1);
        assertThat(record().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.DELIVERED);
        assertThat(accounts.findByPaymentNo("PAY-1").orElseThrow().getStatus())
                .isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        verify(ticket, times(1)).completeVirtualAccountDeposit(any());
    }

    @Test
    void commitFailureRollsBackDepositHistoryAndOutboxWithoutCallingTicket() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            gateway.deposit(request());
            verifyNoInteractions(ticket);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("입금 커밋 실패");
                }
            });
        })).hasMessage("입금 커밋 실패");
        assertThat(accounts.findByPaymentNo("PAY-1").orElseThrow().getStatus())
                .isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(histories.count()).isZero();
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(ticket);
    }

    @Test
    void lostResponseRetriesSameSnapshotAndKeepsDepositCommitted() {
        AtomicBoolean applied = new AtomicBoolean();
        AtomicInteger attempts = new AtomicInteger();
        when(ticket.completeVirtualAccountDeposit(any())).thenAnswer(invocation -> {
            var payload = record().toRequest();
            assertThat(invocation.getArgument(0, Object.class)).usingRecursiveComparison().isEqualTo(payload);
            applied.set(true);
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("티켓 커밋 이후 응답 유실");
            return response();
        });
        gateway.deposit(request());
        assertThat(applied).isTrue();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.PENDING);
        assertThat(record().getRetryCount()).isEqualTo(1);
        dispatcher.retryPendingEvents();
        assertThat(attempts).hasValue(1);
        makeReady();
        dispatcher.retryPendingEvents();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.DELIVERED);
        assertThat(record().getLastErrorType()).isNull();
        assertThat(histories.count()).isEqualTo(1);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void committedPendingOutboxIsRecoveredWhenImmediateDeliveryNeverRan() {
        doNothing().when(dispatcher).deliverSafely(any());
        gateway.deposit(request());
        verifyNoInteractions(ticket);
        assertThat(record().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.PENDING);
        doCallRealMethod().when(dispatcher).deliverSafely(any());
        dispatcher.retryPendingEvents();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.DELIVERED);
    }

    @Test
    void deliveredStateCommitFailureLeavesPendingForSafeRedelivery() {
        AtomicInteger attempts = new AtomicInteger();
        when(ticket.completeVirtualAccountDeposit(any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        throw new IllegalStateException("전달 완료 커밋 실패");
                    }
                });
            }
            return response();
        });
        gateway.deposit(request());
        assertThat(record().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.PENDING);
        dispatcher.retryPendingEvents();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.DELIVERED);
        assertThat(histories.count()).isEqualTo(1);
        assertThat(attempts).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "account", "depositor", "time", "status", "null"})
    void doesNotAcknowledgeMismatchedOrMissingResponse(String mismatch) {
        PaymentResponse response = response();
        switch (mismatch) {
            case "amount" -> response.setAmount(9999);
            case "account" -> response.setAccountNumber("다른 계좌");
            case "depositor" -> response.setDepositorName("다른 입금자");
            case "time" -> response.setPaidAt("2020-01-01 00:00:00");
            case "status" -> response.setStatus(PaymentStatus.WAITING_DEPOSIT);
            case "null" -> response = null;
        }
        when(ticket.completeVirtualAccountDeposit(any())).thenReturn(response);
        gateway.deposit(request());
        assertThat(record().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.PENDING);
        assertThat(record().getRetryCount()).isEqualTo(1);
        assertThat(accounts.findByPaymentNo("PAY-1").orElseThrow().getStatus())
                .isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
    }

    @Test
    void concurrentDispatchersSendOneNotification() throws Exception {
        doNothing().when(dispatcher).deliverSafely(any());
        gateway.deposit(request());
        doCallRealMethod().when(dispatcher).deliverSafely(any());
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        when(ticket.completeVirtualAccountDeposit(any())).thenAnswer(invocation -> {
            sending.countDown();
            await(release);
            return response();
        });
        Long id = record().getOutboxId();
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var first = pool.submit(() -> dispatcher.deliverSafely(id));
                await(sending);
                var second = pool.submit(() -> {
                    secondStarted.countDown();
                    dispatcher.deliverSafely(id);
                });
                await(secondStarted);
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        verify(ticket, times(1)).completeVirtualAccountDeposit(any());
        assertThat(record().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.DELIVERED);
    }

    private VirtualAccountDepositOutbox record() {
        return outbox.findByPaymentNo("PAY-1").orElseThrow();
    }

    private void makeReady() {
        jdbc.update("update virtual_account_deposit_outbox set next_attempt_at = ?", LocalDateTime.now().minusMinutes(1));
    }

    private GatewayVirtualAccountDepositRequest request() {
        return GatewayVirtualAccountDepositRequest.builder()
                .accountNumber("1111-123456").depositorName("입금자").amount(new BigDecimal("10000"))
                .depositedAt(LocalDateTime.of(2026, 8, 19, 12, 0)).build();
    }

    private PaymentResponse response() {
        return PaymentResponse.builder().paymentNo("PAY-1").method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PAID).amount(10000).bankName(BankCompany.KB.getBankName())
                .accountNumber("1111-123456").depositorName("입금자")
                .paidAt(request().getDepositedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시성 테스트 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
