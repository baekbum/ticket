package dev.bum.audit_service.ticketing;

import dev.bum.audit_service.config.KafkaConsumerConfig;
import dev.bum.audit_service.kafka.TicketingDetectionLogConsumer;
import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(TicketingDetectionLogConsumerIntegrationTest.Config.class)
@TestPropertySource(properties = {
        "spring.datasource.username=postgres",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false",
        "topic.ticketing.detection.log.name=ticketing-detection-log",
        "app.kafka.ticketing-detection-log.group-id=ticketing-detection-log-test"
})
@EmbeddedKafka(partitions = 2, topics = {"ticketing-detection-log", "ticketing-detection-log.DLT"})
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "TICKETING_DETECTION_TEST_POSTGRES_PORT", matches = "\\d+")
class TicketingDetectionLogConsumerIntegrationTest {

    @Configuration
    @EnableTransactionManagement
    @EntityScan(basePackageClasses = TicketingDetectionLogEntity.class)
    @EnableJpaRepositories(basePackageClasses = TicketingDetectionLogJpaRepository.class)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, JacksonAutoConfiguration.class, KafkaAutoConfiguration.class})
    @Import({KafkaConsumerConfig.class, TicketingDetectionLogConsumer.class, TicketingDetectionLogPersistenceService.class})
    static class Config {
        @Bean
        KafkaDltSlackNotifier kafkaDltSlackNotifier() {
            return mock(KafkaDltSlackNotifier.class);
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        int port = Integer.parseInt(System.getenv("TICKETING_DETECTION_TEST_POSTGRES_PORT"));
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + port + "/ticketing_detection_test");
    }

    @Autowired KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired JdbcTemplate jdbc;

    @Test
    void repeatedDeliveryKeepsTheOriginalLogAndOtherDetectionTypesAreRecorded() throws Exception {
        String userId = UUID.randomUUID().toString();
        TicketingDetectionType captchaLimit = TicketingDetectionType.CAPTCHA_ATTEMPT_LIMIT_REACHED;
        TicketingDetectionType seatActivity = TicketingDetectionType.SEAT_ACTIVITY_SUSPECTED;
        List<TicketingDetectionType> additionalTypes = List.of(
                TicketingDetectionType.AREA_LAYOUT_ACTIVE_TOKEN_MISSING,
                TicketingDetectionType.AREA_LAYOUT_REFRESH_LIMIT_REACHED,
                TicketingDetectionType.SEAT_OCCUPATION_CAPTCHA_NOT_PASSED,
                TicketingDetectionType.PREPARE_TOO_FAST);
        Instant occurredAt = Instant.parse("2026-11-24T12:34:56Z");
        String firstTraceId = "ab".repeat(16);
        publish(0, detection(userId, captchaLimit, occurredAt, firstTraceId, Map.of("attempts", 5)));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(countFor(userId)).isEqualTo(1));

        publish(1, detection(userId, captchaLimit, occurredAt.plusSeconds(1), "cd".repeat(16), Map.of("attempts", 6)));
        publish(1, detection(userId, seatActivity, occurredAt.plusSeconds(2), null, Map.of()));
        for (TicketingDetectionType type : additionalTypes) {
            publish(1, detection(userId, type, occurredAt.plusSeconds(2), null, Map.of()));
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(countFor(userId)).isEqualTo(6));
        assertThat(jdbc.queryForList("select detection_type from ticketing_detection_log where user_id = ?",
                String.class, userId)).contains(captchaLimit.name(), seatActivity.name())
                .containsAll(additionalTypes.stream().map(Enum::name).toList());
        jdbc.queryForObject("""
                select trace_id, occurred_at, cast(evidence ->> 'attempts' as integer) as attempts
                from ticketing_detection_log where user_id = ? and detection_type = ?
                """, (row, index) -> {
            assertThat(row.getString("trace_id")).isEqualTo(firstTraceId);
            assertThat(row.getObject("occurred_at", OffsetDateTime.class).toInstant()).isEqualTo(occurredAt);
            assertThat(row.getInt("attempts")).isEqualTo(5);
            return true;
        }, userId, captchaLimit.name());
    }

    private void publish(int partition, TicketingDetectionLogEvent event) throws Exception {
        kafkaTemplate.send("ticketing-detection-log", partition, event.getUserId(), event).get(10, TimeUnit.SECONDS);
    }

    private Integer countFor(String userId) {
        return jdbc.queryForObject("select count(*) from ticketing_detection_log where user_id = ?", Integer.class, userId);
    }

    private TicketingDetectionLogEvent detection(
            String userId, TicketingDetectionType detectionType, Instant occurredAt, String traceId,
            Map<String, Object> evidence
    ) {
        return TicketingDetectionLogEvent.builder()
                .eventId(101L)
                .userId(userId)
                .sessionKeyHash("a".repeat(64))
                .detectionType(detectionType)
                .traceId(traceId)
                .occurredAt(occurredAt)
                .evidence(evidence)
                .build();
    }
}
