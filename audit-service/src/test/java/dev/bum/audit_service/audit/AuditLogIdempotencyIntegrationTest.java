package dev.bum.audit_service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.bum.common.kafka.audit.AuditLogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@ImportAutoConfiguration({JacksonAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ContextConfiguration(classes = AuditLogIdempotencyIntegrationTest.PersistenceConfig.class)
@Import({AuditLogPersistenceService.class, AuditLogInsertRepository.class, AuditLogFingerprint.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "AUDIT_IDEMPOTENCY_POSTGRES_PORT", matches = "\\d+")
class AuditLogIdempotencyIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.audit_service.audit")
    @EnableJpaRepositories(basePackages = "dev.bum.audit_service.audit")
    static class PersistenceConfig { }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String port = System.getenv("AUDIT_IDEMPOTENCY_POSTGRES_PORT");
        registry.add("spring.datasource.url", () ->
                "jdbc:postgresql://127.0.0.1:" + Integer.parseInt(port) + "/audit_idempotency_test");
        registry.add("spring.datasource.username", () -> "audit_test");
        registry.add("spring.datasource.password", () -> "audit_test");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired AuditLogPersistenceService service;
    @Autowired AuditLogJpaRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void repeatedDeliveryAndJsonReplayCreateOnlyOneRow() throws Exception {
        AuditLogEvent event = event("key-1", "ticket-service");
        assertThat(service.save(event)).isEqualTo(AuditLogSaveResult.SAVED);
        assertThat(service.save(mapper.readValue(mapper.writeValueAsBytes(event), AuditLogEvent.class)))
                .isEqualTo(AuditLogSaveResult.DUPLICATE);

        assertThat(service.save(event)).isEqualTo(AuditLogSaveResult.DUPLICATE);
        assertThat(repository.count()).isEqualTo(1);
        AuditLogEntity saved = repository.findAll().getFirst();
        assertThat(saved.getRequestHash()).hasSize(64);
        assertThat(saved.getMetadata()).containsEntry("method", "update");
        assertThat(saved.getBeforeData()).containsEntry("title", "before");
        assertThat(saved.getAfterData()).containsEntry("title", "after");
    }

    @ParameterizedTest
    @ValueSource(strings = {"action", "actorId", "occurredAt", "metadata", "result", "beforeData", "reason"})
    void sameKeyWithDifferentContentIsRejectedWithoutChangingOriginal(String field) throws Exception {
        AuditLogEvent event = event("key-1", "ticket-service");
        assertThat(service.save(event)).isEqualTo(AuditLogSaveResult.SAVED);
        ObjectNode changed = mapper.valueToTree(event);
        switch (field) {
            case "occurredAt" -> changed.set(field, mapper.valueToTree(event.getOccurredAt().plusSeconds(1)));
            case "metadata", "beforeData" -> changed.set(field, mapper.valueToTree(Map.of("changed", true)));
            default -> changed.put(field, "changed");
        }
        AuditLogEvent other = mapper.treeToValue(changed, AuditLogEvent.class);

        assertThatThrownBy(() -> service.save(other)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("같은 서비스의 멱등키로 다른 감사 이벤트를 저장할 수 없습니다.");

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findAll().getFirst().getAction()).isEqualTo("EVENT_UPDATE");
        assertThat(service.save(event)).isEqualTo(AuditLogSaveResult.DUPLICATE);
    }

    @Test
    void differentServicesCanUseTheSameKey() {
        assertThat(service.save(event("shared-key", "ticket-service"))).isEqualTo(AuditLogSaveResult.SAVED);
        assertThat(service.save(event("shared-key", "user-service"))).isEqualTo(AuditLogSaveResult.SAVED);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void separateOperationsWithTheSameContentAreBothSaved() {
        assertThat(service.save(event("key-1", "ticket-service"))).isEqualTo(AuditLogSaveResult.SAVED);
        assertThat(service.save(event("key-2", "ticket-service"))).isEqualTo(AuditLogSaveResult.SAVED);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void outerRollbackRemovesReceiptAndRetryCanSave() {
        AuditLogEvent event = event("key-1", "ticket-service");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThat(service.save(event)).isEqualTo(AuditLogSaveResult.SAVED);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback");

        assertThat(repository.count()).isZero();
        assertThat(service.save(event)).isEqualTo(AuditLogSaveResult.SAVED);
    }

    @Test
    void otherDatabaseConstraintFailuresAreNotTreatedAsDuplicates() throws Exception {
        ObjectNode changed = mapper.valueToTree(event("key-1", "ticket-service"));
        changed.putNull("action");
        AuditLogEvent invalid = mapper.treeToValue(changed, AuditLogEvent.class);

        assertThatThrownBy(() -> service.save(invalid)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.count()).isZero();
        assertThat(service.save(event("key-1", "ticket-service"))).isEqualTo(AuditLogSaveResult.SAVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "blank", "long", "service", "time"})
    void malformedIdentityIsRejectedWithoutGeneratingAnotherKey(String field) throws Exception {
        ObjectNode changed = mapper.valueToTree(event("key-1", "ticket-service"));
        switch (field) {
            case "missing" -> changed.remove("idempotencyKey");
            case "blank" -> changed.put("idempotencyKey", " ");
            case "long" -> changed.put("idempotencyKey", "x".repeat(101));
            case "service" -> changed.put("serviceName", " ");
            case "time" -> changed.putNull("occurredAt");
        }
        AuditLogEvent invalid = mapper.treeToValue(changed, AuditLogEvent.class);

        assertThatThrownBy(() -> service.save(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void concurrentDuplicateWaitsForCommitAndCreatesOnlyOneRow() throws Exception {
        assertConcurrentInsert(false);
    }

    @Test
    void concurrentRequestCanInsertAfterTheFirstTransactionRollsBack() throws Exception {
        assertConcurrentInsert(true);
    }

    private void assertConcurrentInsert(boolean rollbackFirst) throws Exception {
        AuditLogEvent event = event("concurrent-key", "ticket-service");
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var first = executor.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    AuditLogSaveResult result = service.save(event);
                    inserted.countDown();
                    await(release);
                    if (rollbackFirst) tx.setRollbackOnly();
                    return result;
                }));
                assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    return service.save(event);
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(AuditLogSaveResult.SAVED);
                assertThat(second.get(10, TimeUnit.SECONDS))
                        .isEqualTo(rollbackFirst ? AuditLogSaveResult.SAVED : AuditLogSaveResult.DUPLICATE);
            } finally {
                release.countDown();
            }
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", e);
        }
    }

    private AuditLogEvent event(String key, String serviceName) {
        return AuditLogEvent.builder()
                .idempotencyKey(key).serviceName(serviceName)
                .occurredAt(LocalDateTime.of(2026, 10, 25, 12, 0, 0, 123456789))
                .actorType("ADMIN").actorId("admin").actorName("관리자")
                .action("EVENT_UPDATE").targetType("EVENT").targetId("1").result("SUCCESS")
                .requestId("request-1").traceId("trace-1").ipAddress("127.0.0.1").userAgent("test-agent")
                .beforeData(Map.of("title", "before")).afterData(Map.of("title", "after"))
                .metadata(Map.of("method", "update", "seats", List.of(1, 2))).build();
    }
}
