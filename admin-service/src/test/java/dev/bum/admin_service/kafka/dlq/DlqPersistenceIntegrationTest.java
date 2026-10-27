package dev.bum.admin_service.kafka.dlq;

import dev.bum.admin_service.kafka.dlq.dto.request.DlqMessageHandleRequest;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqMessageSummaryResponse;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleAction;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleHistory;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleHistoryJpaRepository;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleStatus;
import dev.bum.admin_service.kafka.dlq.ingestion.DlqMessageConsumer;
import dev.bum.admin_service.kafka.dlq.ingestion.DlqMessageIngestionService;
import dev.bum.admin_service.kafka.dlq.message.DlqMessage;
import dev.bum.admin_service.kafka.dlq.message.DlqMessageJpaRepository;
import dev.bum.admin_service.kafka.dlq.message.DlqMessageStatus;
import dev.bum.admin_service.kafka.dlq.message.KafkaDlqQueryService;
import dev.bum.admin_service.kafka.dlq.replay.DlqReplayTransactionService;
import dev.bum.admin_service.kafka.dlq.replay.KafkaDlqReplayService;
import dev.bum.admin_service.kafka.dlq.support.DlqRecordCodec;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.config.KafkaDlqConfig;
import dev.bum.admin_service.config.KafkaDlqProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
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

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@DataJpaTest(showSql = false)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@ContextConfiguration(classes = DlqPersistenceIntegrationTest.PersistenceConfig.class)
@Import({DlqMessageIngestionService.class, DlqRecordCodec.class, DlqReplayTransactionService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "DLQ_POSTGRES_PORT", matches = "\\d+")
class DlqPersistenceIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {DlqMessage.class, DlqMessageHandleHistory.class})
    @EnableJpaRepositories(basePackageClasses = {DlqMessageJpaRepository.class, DlqMessageHandleHistoryJpaRepository.class})
    static class PersistenceConfig {}

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + System.getenv("DLQ_POSTGRES_PORT") + "/dlq_test");
        registry.add("spring.datasource.username", () -> "dlq_test");
        registry.add("spring.datasource.password", () -> "dlq_test");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired DlqMessageIngestionService ingestion;
    @Autowired DlqReplayTransactionService replay;
    @Autowired DlqMessageJpaRepository messages;
    @Autowired DlqMessageHandleHistoryJpaRepository histories;
    @Autowired DlqRecordCodec codec;
    @Autowired PlatformTransactionManager manager;

    private final DlqMessageHandleRequest request = new DlqMessageHandleRequest("user-event.DLT", 0, 10L, "admin", "복구");

    @BeforeEach
    void clean() {
        messages.deleteAll();
        histories.deleteAll();
    }

    private ConsumerRecord<byte[], byte[]> record() {
        return new ConsumerRecord<>("user-event.DLT", 0, 10L, new byte[]{0, -1}, new byte[]{1, 2, -1});
    }

    @Test
    void repeatedCollectionPreservesRawBytesDuplicateHeadersAndProcessingState() {
        var record = record();
        record.headers().add("trace", new byte[]{-1}).add("trace", null);
        ingestion.save(record);
        var attempt = replay.begin(request, "user-event", DlqMessageHandleAction.REPLAY, null);
        ingestion.save(record);
        assertThat(messages.count()).isEqualTo(1);
        var saved = messages.findAll().getFirst();
        assertThat(saved.getPayload()).containsExactly(1, 2, -1);
        assertThat(saved.getMessageKey()).containsExactly(0, -1);
        assertThat(codec.headers(saved.getHeadersJson())).hasSize(2);
        assertThat(codec.headers(saved.getHeadersJson()).get(1).value()).isNull();
        assertThat(saved.getStatus()).isEqualTo(DlqMessageStatus.REPLAYING);
        assertThat(saved.getCurrentHistoryId()).isEqualTo(attempt.historyId());
    }

    @Test
    void nullKeyAndTombstoneCanBeCollected() {
        ingestion.save(new ConsumerRecord<>("user-event.DLT", 0, 10L, null, null));
        assertThat(messages.findAll().getFirst().getPayload()).isNull();
    }

    @Test
    void collectionRollsBackWithDbTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
            ingestion.save(record());
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(messages.count()).isZero();
    }

    @Test
    void simultaneousCollectionCreatesOneRow() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); ingestion.save(record()); return true; });
            var second = executor.submit(() -> { start.await(); ingestion.save(record()); return true; });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(messages.count()).isEqualTo(1);
    }

    @Test
    void concurrentReplayOnlyOneClaimSurvives() throws Exception {
        ingestion.save(record());
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> claim = () -> {
                start.await();
                try {
                    replay.begin(request, "user-event", DlqMessageHandleAction.REPLAY, null);
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            };
            var first = executor.submit(claim);
            var second = executor.submit(claim);
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(histories.count()).isEqualTo(1);
    }

    @Test
    void independentClaimSurvivesOuterRollback() {
        ingestion.save(record());
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
            replay.begin(request, "user-event", DlqMessageHandleAction.REPLAY, null);
            throw new IllegalStateException("outer rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(histories.count()).isEqualTo(1);
        assertThat(messages.findAll().getFirst().getStatus()).isEqualTo(DlqMessageStatus.REPLAYING);
    }

    @Test
    void successUpdatesBothRowsAndBlocksAnotherAttempt() {
        ingestion.save(record());
        var attempt = replay.begin(request, "user-event", DlqMessageHandleAction.REPLAY, null);
        replay.finish(request, attempt.historyId(), DlqMessageHandleStatus.SUCCESS, null);
        assertThat(messages.findAll().getFirst().getStatus()).isEqualTo(DlqMessageStatus.REPLAYED);
        assertThat(histories.findById(attempt.historyId()).orElseThrow().getStatus()).isEqualTo(DlqMessageHandleStatus.SUCCESS);
        assertThatThrownBy(() -> replay.begin(request, "user-event", DlqMessageHandleAction.REPLAY, null)).isInstanceOf(IllegalStateException.class);
        assertThat(histories.count()).isEqualTo(1);
    }

    @Test
    void failedAttemptCanBeManuallyRetriedButOldCompletionCannotOverwriteIt() {
        ingestion.save(record());
        var first = replay.begin(request, "user-event", DlqMessageHandleAction.REPLAY, null);
        replay.finish(request, first.historyId(), DlqMessageHandleStatus.UNKNOWN, "TimeoutException");
        var second = replay.begin(request, "user-event", DlqMessageHandleAction.MODIFIED_REPLAY, "{}");
        assertThatThrownBy(() -> replay.finish(request, first.historyId(), DlqMessageHandleStatus.SUCCESS, null)).isInstanceOf(IllegalStateException.class);
        replay.finish(request, second.historyId(), DlqMessageHandleStatus.SUCCESS, null);
        assertThat(messages.findAll().getFirst().getStatus()).isEqualTo(DlqMessageStatus.MODIFIED_REPLAYED);
        assertThat(histories.count()).isEqualTo(2);
    }

    @Test
    void stalledClaimRequiresAgeAndExplicitRecovery() {
        ingestion.save(record());
        var attempt = replay.begin(request, "user-event", DlqMessageHandleAction.REPLAY, null);
        assertThatThrownBy(() -> replay.releaseStalled(request)).isInstanceOf(IllegalStateException.class);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            DlqMessage message = messages.findForUpdate("user-event.DLT", 0, 10L).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(message, "updatedAt", LocalDateTime.now().minusMinutes(11));
        });
        replay.releaseStalled(request);
        assertThat(messages.findAll().getFirst().getStatus()).isEqualTo(DlqMessageStatus.UNKNOWN);
        assertThat(histories.findById(attempt.historyId()).orElseThrow().getStatus()).isEqualTo(DlqMessageHandleStatus.UNKNOWN);
    }

    @Test
    void discardCommitsStateAndHistoryTogether() {
        ingestion.save(record());
        var attempt = replay.begin(request, null, DlqMessageHandleAction.DISCARD, null);
        assertThat(messages.findAll().getFirst().getStatus()).isEqualTo(DlqMessageStatus.DISCARDED);
        assertThat(histories.findById(attempt.historyId()).orElseThrow().getStatus()).isEqualTo(DlqMessageHandleStatus.SUCCESS);
    }

    @Test
    void preexistingSuccessIsNotImportedAsPending() {
        histories.save(DlqMessageHandleHistory.builder().dltTopic("user-event.DLT").partitionNo(0).messageOffset(10L)
                .action(DlqMessageHandleAction.REPLAY).status(DlqMessageHandleStatus.SUCCESS).operator("a").build());
        ingestion.save(record());
        assertThat(messages.findAll().getFirst().getStatus()).isEqualTo(DlqMessageStatus.REPLAYED);
    }

    @Test
    void dbQuerySupportsStatusAndPaging() {
        for (long offset = 10; offset < 13; offset++) {
            ingestion.save(new ConsumerRecord<>("user-event.DLT", 0, offset, null, "{}".getBytes()));
        }
        replay.begin(request, null, DlqMessageHandleAction.DISCARD, null);
        KafkaDlqProperties properties = new KafkaDlqProperties();
        properties.setMappings(Map.of("user-event.DLT", "user-event"));
        var query = new KafkaDlqQueryService(properties, messages, histories, codec);
        assertThat(query.messages("user-event.DLT", 0, 10L, 1, 0, DlqMessageStatus.PENDING)).extracting(DlqMessageSummaryResponse::getOffset).containsExactly(11L);
        assertThat(query.messages("user-event.DLT", 0, 10L, 1, 1, DlqMessageStatus.PENDING)).extracting(DlqMessageSummaryResponse::getOffset).containsExactly(12L);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DLQ_KAFKA_BOOTSTRAP", matches = ".+")
    void realKafkaCommitsOffsetOnlyAfterDbSaveAndReplaysFromDb() throws Exception {
        String bootstrap = System.getenv("DLQ_KAFKA_BOOTSTRAP");
        String topic = "dlq-test-" + UUID.randomUUID();
        String dlt = topic + ".DLT";
        String group = topic + "-inbox";
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of(bootstrap));
        KafkaDlqConfig config = new KafkaDlqConfig();
        var consumerFactory = config.kafkaDlqConsumerFactory(kafkaProperties);
        var listenerFactory = config.kafkaDlqInboxListenerFactory(consumerFactory, true);
        var container = listenerFactory.createContainer(dlt);
        container.getContainerProperties().setGroupId(group);
        AtomicBoolean allowSave = new AtomicBoolean(false);
        AtomicInteger attempts = new AtomicInteger();
        DlqMessageConsumer listener = new DlqMessageConsumer(ingestion);
        container.setupMessageListener((MessageListener<byte[], byte[]>) record -> {
            attempts.incrementAndGet();
            if (!allowSave.get()) throw new IllegalStateException("DB 저장 전 장애");
            listener.consume(record);
        });
        var producerFactory = new DefaultKafkaProducerFactory<byte[], byte[]>(Map.of(
                "bootstrap.servers", bootstrap, "key.serializer", ByteArraySerializer.class,
                "value.serializer", ByteArraySerializer.class, "acks", "all"
        ));
        var template = new KafkaTemplate<>(producerFactory);
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", bootstrap))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1), new NewTopic(dlt, 1, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
            var sent = new ProducerRecord<byte[], byte[]>(dlt, new byte[]{0, -1}, "{\"idempotencyKey\":\"same-key\"}".getBytes());
            sent.headers().add("trace", new byte[]{1}).add("trace", new byte[]{2});
            template.send(sent).get(20, TimeUnit.SECONDS);
            container.start();
            // 빈 브로커는 내부 오프셋 토픽과 그룹 코디네이터를 처음 생성하는 시간이 필요하다.
            await().atMost(Duration.ofSeconds(60)).until(() -> attempts.get() > 0);
            assertThat(messages.count()).isZero();
            var committed = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            assertThat(committed.get(new TopicPartition(dlt, 0))).isNull();
            allowSave.set(true);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(messages.count()).isEqualTo(1);
                var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
                assertThat(offsets.get(new TopicPartition(dlt, 0))).isNotNull();
                assertThat(offsets.get(new TopicPartition(dlt, 0)).offset()).isEqualTo(1);
            });
            KafkaDlqProperties properties = new KafkaDlqProperties();
            properties.setMappings(Map.of(dlt, topic));
            var service = new KafkaDlqReplayService(properties, template, replay, codec, new ObjectMapper());
            var result = service.replay(new DlqMessageHandleRequest(dlt, 0, 0L, "admin", "실제 재발행 검증"));
            assertThat(result.getResult()).isEqualTo("REPLAYED");
            try (var consumer = consumerFactory.createConsumer(topic + "-verification", "test")) {
                var partition = new TopicPartition(topic, 0);
                consumer.assign(List.of(partition));
                consumer.seek(partition, 0L);
                await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                    var records = consumer.poll(Duration.ofMillis(500)).records(partition);
                    assertThat(records).hasSize(1);
                    assertThat(records.getFirst().key()).containsExactly(0, -1);
                    assertThat(records.getFirst().value()).isEqualTo(sent.value());
                    assertThat(records.getFirst().headers().headers("trace")).hasSize(2);
                });
            }
            assertThat(messages.findAll().getFirst().getStatus()).isEqualTo(DlqMessageStatus.REPLAYED);
        } finally {
            container.stop();
            producerFactory.destroy();
        }
    }
}
