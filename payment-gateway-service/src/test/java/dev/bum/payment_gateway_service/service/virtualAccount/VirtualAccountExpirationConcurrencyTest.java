package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEventJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentHistoryType;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@ContextConfiguration(classes = VirtualAccountExpirationConcurrencyTest.PersistenceConfig.class)
@Import(VirtualAccountExpirationScheduler.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VirtualAccountExpirationConcurrencyTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.payment_gateway_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.payment_gateway_service.jpa")
    static class PersistenceConfig { }

    @Autowired private VirtualAccountExpirationScheduler scheduler;
    @Autowired private DummyVirtualAccountJpaRepository accounts;
    @Autowired private DummyVirtualAccountPaymentHistoryJpaRepository histories;
    @Autowired private VirtualAccountOutboxEventJpaRepository outbox;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String port = System.getenv("GATEWAY_TEST_POSTGRES_PORT");
        if (port != null && !port.isBlank()) {
            // 개발 DB가 아닌 검증 전용 DB에서만 테이블을 생성·삭제한다.
            properties.add("spring.datasource.url", () ->
                    "jdbc:postgresql://127.0.0.1:" + Integer.parseInt(port) + "/gateway_expiration_test");
            properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            properties.add("spring.datasource.username", () -> "gateway_test");
            properties.add("spring.datasource.password", () -> "gateway_test");
        } else {
            properties.add("spring.datasource.url", () ->
                    "jdbc:h2:mem:gateway-expiration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            properties.add("spring.datasource.username", () -> "sa");
            properties.add("spring.datasource.password", () -> "");
        }
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void clearDatabase() {
        outbox.deleteAll();
        histories.deleteAll();
        accounts.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expirationWaitsForDepositAndRechecksAfterCommitOrRollback(boolean rollback) throws Exception {
        DummyVirtualAccount account = waitingAccount();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch expirationStarted = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var deposit = pool.submit(() -> transaction().executeWithoutResult(status -> {
                    // 실제 입금 서비스와 같은 잠금 조회로 입금을 반영하고 커밋을 늦춘다.
                    DummyVirtualAccount locked = accounts.findByAccountNumber(account.getAccountNumber()).orElseThrow();
                    locked.deposit("입금자", locked.getExpiresAt().minusSeconds(1));
                    histories.save(DummyVirtualAccountPaymentHistory.deposited(locked));
                    entityManager.flush();
                    holding.countDown();
                    await(release);
                    if (rollback) status.setRollbackOnly();
                }));
                await(holding);
                var expiration = pool.submit(() -> {
                    expirationStarted.countDown();
                    scheduler.expireWaitingAccounts();
                });
                await(expirationStarted);
                assertThatThrownBy(() -> expiration.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                deposit.get(10, TimeUnit.SECONDS);
                expiration.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }

        assertThat(accounts.findById(account.getVirtualAccountId()).orElseThrow().getStatus())
                .isEqualTo(rollback ? VirtualAccountPaymentStatus.EXPIRED : VirtualAccountPaymentStatus.DEPOSITED);
        assertThat(expiredHistoryCount()).isEqualTo(rollback ? 1 : 0);
        assertThat(outbox.count()).isEqualTo(rollback ? 1 : 0);
    }

    @Test
    void depositWaitsForExpirationAndReadsExpiredState() throws Exception {
        DummyVirtualAccount account = waitingAccount();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch depositStarted = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var expiration = pool.submit(() -> transaction().executeWithoutResult(status -> {
                    scheduler.expireWaitingAccounts();
                    holding.countDown();
                    await(release);
                }));
                await(holding);
                var deposit = pool.submit(() -> transaction().execute(status -> {
                    depositStarted.countDown();
                    return accounts.findByAccountNumber(account.getAccountNumber()).orElseThrow().getStatus();
                }));
                await(depositStarted);
                assertThatThrownBy(() -> deposit.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                expiration.get(10, TimeUnit.SECONDS);
                assertThat(deposit.get(10, TimeUnit.SECONDS)).isEqualTo(VirtualAccountPaymentStatus.EXPIRED);
            } finally {
                release.countDown();
            }
        }
        assertThat(expiredHistoryCount()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void simultaneousSchedulersCreateOnlyOneExpirationHistoryAndOutbox() throws Exception {
        waitingAccount();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var first = pool.submit(() -> transaction().executeWithoutResult(status -> {
                    scheduler.expireWaitingAccounts();
                    holding.countDown();
                    await(release);
                }));
                await(holding);
                var second = pool.submit(() -> {
                    secondStarted.countDown();
                    scheduler.expireWaitingAccounts();
                });
                await(secondStarted);
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertThat(expiredHistoryCount()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void failedExpirationRollsBackStateHistoryAndOutboxTogether() {
        DummyVirtualAccount account = waitingAccount();
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            scheduler.expireWaitingAccounts();
            throw new IllegalStateException("만료 반영 이후 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(accounts.findById(account.getVirtualAccountId()).orElseThrow().getStatus())
                .isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(histories.count()).isZero();
        assertThat(outbox.count()).isZero();

        scheduler.expireWaitingAccounts();
        assertThat(expiredHistoryCount()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    private DummyVirtualAccount waitingAccount() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return accounts.save(DummyVirtualAccount.builder()
                .paymentNo("PAY-" + suffix)
                .bankCompany(BankCompany.KB)
                .bankName(BankCompany.KB.getBankName())
                .accountNumber(suffix)
                .amount(BigDecimal.valueOf(180000))
                .status(VirtualAccountPaymentStatus.WAITING_DEPOSIT)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build());
    }

    private long expiredHistoryCount() {
        return histories.findAll().stream()
                .filter(history -> history.getHistoryType() == VirtualAccountPaymentHistoryType.EXPIRED)
                .count();
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setTimeout(15);
        return transaction;
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
