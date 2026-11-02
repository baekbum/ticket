package dev.bum.ticket_service.jpa.checkout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class CheckoutAttemptJpaRepositoryTest {

    @Autowired
    private CheckoutAttemptJpaRepository checkoutAttemptJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("prepare 좌석 ID 목록은 DB 재조회 후에도 유지된다")
    void persists_prepared_seat_ids() {
        checkoutAttemptJpaRepository.saveAndFlush(CheckoutAttempt.prepare(
                "idem-seats", "user01", "order-seats", 1L, Set.of(101L, 202L), "PAY-SEATS",
                LocalDateTime.now().plusMinutes(10)));
        entityManager.clear();

        CheckoutAttempt attempt = checkoutAttemptJpaRepository
                .findByIdempotencyKeyForUpdate("idem-seats").orElseThrow();

        assertThat(attempt.getSeatIds()).containsExactlyInAnyOrder(101L, 202L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 멱등 키를 조회하는 두 번째 트랜잭션은 첫 번째 비관적 잠금이 끝날 때까지 대기한다")
    void find_by_idempotency_key_for_update_serializes_concurrent_transactions() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> checkoutAttemptJpaRepository.saveAndFlush(
                CheckoutAttempt.prepare(
                        "idem-1",
                        "user01",
                        "order-1",
                        1L,
                        Set.of(1L),
                        "PAY-1",
                        LocalDateTime.now().plusMinutes(10)
                )
        ));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondTransactionLocked = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1").orElseThrow();
                firstTransactionLocked.countDown();
                await(releaseFirstTransaction);
            }));

            assertThat(firstTransactionLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                checkoutAttemptJpaRepository.findByIdempotencyKeyForUpdate("idem-1").orElseThrow();
                secondTransactionLocked.countDown();
            }));

            assertThat(secondTransactionLocked.await(300, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirstTransaction.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);

            assertThat(secondTransactionLocked.getCount()).isZero();
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 대기 중 인터럽트가 발생했습니다.", e);
        }
    }
}
