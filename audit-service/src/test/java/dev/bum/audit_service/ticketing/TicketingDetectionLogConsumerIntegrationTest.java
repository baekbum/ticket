package dev.bum.audit_service.ticketing;

import dev.bum.audit_service.config.KafkaConsumerConfig;
import dev.bum.audit_service.kafka.TicketingDetectionLogConsumer;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import dev.bum.common.service.audit.dto.TicketingDetectionLogCondRequest;
import dev.bum.common.service.audit.dto.TicketingDetectionLogDetailResponse;
import dev.bum.common.service.audit.dto.TicketingDetectionLogListResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired TicketingDetectionLogPersistenceService persistenceService;

    @Test
    void repeatedDeliveryKeepsTheOriginalLogAndOtherDetectionTypesAreRecorded() throws Exception {
        String userId = UUID.randomUUID().toString();
        TicketingDetectionType captchaLimit = TicketingDetectionType.CAPTCHA_ATTEMPT_LIMIT_REACHED;
        Map<TicketingDetectionType, Map<String, Object>> additionalEvidence = Map.of(
                TicketingDetectionType.AREA_LAYOUT_ACTIVE_TOKEN_MISSING, Map.of(),
                TicketingDetectionType.AREA_LAYOUT_REFRESH_LIMIT_REACHED, Map.of("refreshCount", 30, "windowMs", 60_000),
                TicketingDetectionType.SEAT_OCCUPATION_CAPTCHA_NOT_PASSED, Map.of(),
                TicketingDetectionType.PREPARE_TOO_FAST, Map.of("elapsedMs", 3600, "limitMs", 5000));
        Instant occurredAt = Instant.parse("2026-11-24T12:34:56Z");
        String firstTraceId = "ab".repeat(16);
        publish(0, detection(userId, captchaLimit, occurredAt, firstTraceId, Map.of("attempts", 5)));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(countFor(userId)).isEqualTo(1));

        publish(1, detection(userId, captchaLimit, occurredAt.plusSeconds(1), "cd".repeat(16), Map.of("attempts", 6)));
        for (Map.Entry<TicketingDetectionType, Map<String, Object>> entry : additionalEvidence.entrySet()) {
            publish(1, detection(userId, entry.getKey(), occurredAt.plusSeconds(2), null, entry.getValue()));
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(countFor(userId)).isEqualTo(1 + additionalEvidence.size()));
        assertThat(jdbc.queryForList("select detection_type from ticketing_detection_log where user_id = ?",
                String.class, userId)).contains(captchaLimit.name())
                .containsAll(additionalEvidence.keySet().stream().map(Enum::name).toList());

        TicketingDetectionLogDetailResponse captchaDetail = selectDetail(userId, captchaLimit);
        assertThat(captchaDetail.traceId()).isEqualTo(firstTraceId);
        assertThat(captchaDetail.occurredAt()).isEqualTo(occurredAt);
        assertThat(captchaDetail.sessionKeyHash()).isEqualTo("a".repeat(64));
        assertThat(captchaDetail.evidence()).containsExactlyEntriesOf(Map.of("attempts", 5));
        additionalEvidence.forEach((type, evidence) -> {
            TicketingDetectionLogDetailResponse detail = selectDetail(userId, type);
            assertThat(detail.detectionType()).isEqualTo(type);
            assertThat(detail.evidence()).containsExactlyInAnyOrderEntriesOf(evidence);
        });
        assertThatThrownBy(() -> persistenceService.selectById(Long.MAX_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private TicketingDetectionLogDetailResponse selectDetail(String userId, TicketingDetectionType type) {
        Long id = jdbc.queryForObject("select id from ticketing_detection_log where user_id = ? and detection_type = ?",
                Long.class, userId, type.name());
        return persistenceService.selectById(id);
    }

    @Test
    void list_filters_inclusive_dates_and_pages_in_a_stable_newest_first_order() {
        String buyer = UUID.randomUUID().toString();
        TicketingDetectionType type = TicketingDetectionType.PREPARE_TOO_FAST;
        Instant from = Instant.parse("2026-11-24T12:34:56Z");
        Instant to = from.plusSeconds(60);
        long oldest = insertLog(301L, buyer, type, from);
        long firstAtUpperBound = insertLog(301L, buyer, type, to);
        long secondAtUpperBound = insertLog(301L, buyer, type, to);
        insertLog(301L, buyer, type, from.minusSeconds(1));
        insertLog(301L, buyer, type, to.plusSeconds(1));
        insertLog(302L, buyer, type, to);
        insertLog(301L, buyer + "-other", type, to);
        insertLog(301L, buyer, TicketingDetectionType.CAPTCHA_ATTEMPT_LIMIT_REACHED, to);

        CustomPageResponse<TicketingDetectionLogListResponse> first = persistenceService.selectByCond(
                new TicketingDetectionLogCondRequest(301L, " " + buyer + " ", type, from, to, 0, 2));
        CustomPageResponse<TicketingDetectionLogListResponse> second = persistenceService.selectByCond(
                new TicketingDetectionLogCondRequest(301L, buyer, type, from, to, 1, 2));

        assertThat(first.getContent()).extracting(TicketingDetectionLogListResponse::id)
                .containsExactly(secondAtUpperBound, firstAtUpperBound);
        assertThat(first.getPage().getTotalElements()).isEqualTo(3);
        assertThat(first.getPage().getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).extracting(TicketingDetectionLogListResponse::id).containsExactly(oldest);
        assertThat(second.getPage().getNumber()).isEqualTo(1);
        assertThatThrownBy(() -> persistenceService.selectByCond(
                new TicketingDetectionLogCondRequest(301L, buyer, type, to, from, 0, 2)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private long insertLog(Long eventId, String userId, TicketingDetectionType type, Instant occurredAt) {
        return jdbc.queryForObject("""
                insert into ticketing_detection_log (event_id, user_id, detection_type, session_key_hash, occurred_at)
                values (?, ?, ?, ?, ?) returning id
                """, Long.class, eventId, userId, type.name(), UUID.randomUUID().toString().replace("-", "").repeat(2),
                OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
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
