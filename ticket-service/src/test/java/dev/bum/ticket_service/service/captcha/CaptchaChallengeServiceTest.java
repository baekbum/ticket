package dev.bum.ticket_service.service.captcha;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.ticket_service.config.CaptchaPoolProperties;
import dev.bum.ticket_service.config.QueueAccessProperties;
import dev.bum.ticket_service.exception.captcha.CaptchaAttemptLimitException;
import dev.bum.ticket_service.exception.captcha.CaptchaRequiredException;
import dev.bum.ticket_service.exception.captcha.CaptchaChallengeChangedException;
import dev.bum.ticket_service.exception.captcha.CaptchaChallengeExpiredException;
import dev.bum.ticket_service.exception.captcha.CaptchaRefreshRateLimitedException;
import dev.bum.ticket_service.exception.captcha.CaptchaUnavailableException;
import dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException;
import dev.bum.ticket_service.feign.queue.QueueServiceClient;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.kafka.ticketing.TicketingDetectionLogProducer;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CaptchaChallengeServiceTest {

    @Test
    void event_without_captcha_does_not_consume_a_prepared_image() {
        EventRepository events = mock(EventRepository.class);
        given(events.selectById(1L)).willReturn(Event.builder().captchaEnabled(false).build());
        QueueAccessService queue = mock(QueueAccessService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        CaptchaChallengeService service = new CaptchaChallengeService(events, queue, redis, new ObjectMapper(),
                mock(TicketingDetectionLogProducer.class), Optional.empty());

        assertThat(service.issue(1L, "user01", null)).isEmpty();
        verifyNoInteractions(queue, redis);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TICKET_CAPTCHA_TEST_REDIS_PORT", matches = "\\d+")
    void validates_tokens_and_reuses_one_issued_challenge_across_concurrent_requests() throws Exception {
        LettuceConnectionFactory connection = openConnection(1);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connection);
            ObjectMapper mapper = new ObjectMapper();
            CaptchaImagePool pool = new CaptchaImagePool(
                    redis, mapper, new CaptchaImageGenerator(), new CaptchaPoolProperties(2, 2));
            pool.refill();
            JsonNode prepared = mapper.readTree(redis.opsForList().index(CaptchaImagePool.KEY, 0));

            EventRepository events = mock(EventRepository.class);
            given(events.selectById(1L)).willReturn(Event.builder().captchaEnabled(true).build());
            QueueServiceClient queue = mock(QueueServiceClient.class);
            given(queue.validate(new QueueValidateRequest(1L, "user01", "invalid")))
                    .willReturn(new QueueValidateResponse(false, "INVALID_QUEUE_TOKEN"));
            given(queue.validate(new QueueValidateRequest(1L, "user01", "active-one")))
                    .willReturn(new QueueValidateResponse(true, "OK"));
            given(queue.validate(new QueueValidateRequest(1L, "user02", "active-two")))
                    .willReturn(new QueueValidateResponse(true, "OK"));
            CaptchaChallengeService service = new CaptchaChallengeService(
                    events, new QueueAccessService(new QueueAccessProperties(), queue), redis, mapper,
                    mock(TicketingDetectionLogProducer.class), Optional.empty());

            assertThatThrownBy(() -> service.issue(1L, "user01", "invalid"))
                    .isInstanceOf(ActiveTokenExpiredException.class);
            assertThat(redis.opsForList().size(CaptchaImagePool.KEY)).isEqualTo(2);

            CaptchaChallengeResponse first;
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<CaptchaChallengeResponse> a = executor.submit(
                        () -> service.issue(1L, "user01", "active-one").orElseThrow());
                Future<CaptchaChallengeResponse> b = executor.submit(
                        () -> service.issue(1L, "user01", "active-one").orElseThrow());
                first = a.get(5, TimeUnit.SECONDS);
                assertThat(b.get(5, TimeUnit.SECONDS)).isEqualTo(first);
            }
            assertThat(redis.opsForList().size(CaptchaImagePool.KEY)).isEqualTo(1);
            assertThat(first.imageBase64()).isEqualTo(prepared.get("png").asText());
            assertThat(first.expiresAt()).isAfter(Instant.now());

            String issuedKey = CaptchaChallengeService.issuedKey(1L, "user01", "active-one");
            JsonNode saved = mapper.readTree(redis.opsForValue().get(issuedKey));
            assertThat(saved.get("answer").asText()).isEqualTo(prepared.get("answer").asText());
            assertThat(saved.get("challengeId").asText()).isEqualTo(first.challengeId().toString());
            assertThat(redis.getExpire(issuedKey, TimeUnit.MILLISECONDS)).isBetween(1L, 120_000L);
            assertThat(redis.hasKey(CaptchaChallengeService.issuedKey(2L, "user01", "active-one"))).isFalse();
            assertThat(redis.hasKey(CaptchaChallengeService.issuedKey(1L, "user02", "active-one"))).isFalse();
            assertThat(redis.hasKey(CaptchaChallengeService.issuedKey(1L, "user01", "active-two"))).isFalse();

            CaptchaChallengeResponse second = service.issue(1L, "user02", "active-two").orElseThrow();
            assertThat(second.challengeId()).isNotEqualTo(first.challengeId());
            assertThat(redis.opsForList().size(CaptchaImagePool.KEY)).isZero();
            assertThat(service.issue(1L, "user01", "active-one")).contains(first);

            redis.expire(issuedKey, 0, TimeUnit.SECONDS);
            assertThatThrownBy(() -> service.issue(1L, "user01", "active-one"))
                    .isInstanceOf(CaptchaUnavailableException.class);
        } finally {
            connection.destroy();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TICKET_CAPTCHA_TEST_REDIS_PORT", matches = "\\d+")
    void each_refresh_replaces_the_current_challenge_and_counts_toward_the_session_limit() throws Exception {
        LettuceConnectionFactory connection = openConnection(2);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connection);
            ObjectMapper mapper = new ObjectMapper();
            CaptchaImageGenerator generator = new CaptchaImageGenerator();
            new CaptchaImagePool(redis, mapper, generator, new CaptchaPoolProperties(1, 1)).refill();

            EventRepository events = mock(EventRepository.class);
            given(events.selectById(1L)).willReturn(Event.builder().captchaEnabled(true).build());
            QueueAccessService queue = mock(QueueAccessService.class);
            CaptchaChallengeService service = new CaptchaChallengeService(events, queue, redis, mapper,
                    mock(TicketingDetectionLogProducer.class), Optional.empty());
            CaptchaChallengeResponse initial = service.issue(1L, "user01", "active-token").orElseThrow();

            assertThatThrownBy(() -> service.refresh(1L, "user01", "active-token"))
                    .isInstanceOf(CaptchaUnavailableException.class);
            assertThat(service.issue(1L, "user01", "active-token")).contains(initial);

            new CaptchaImagePool(redis, mapper, generator, new CaptchaPoolProperties(8, 8)).refill();
            CaptchaChallengeResponse refreshed;
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<CaptchaChallengeResponse> first = executor.submit(
                        () -> service.refresh(1L, "user01", "active-token").orElseThrow());
                Future<CaptchaChallengeResponse> second = executor.submit(
                        () -> service.refresh(1L, "user01", "active-token").orElseThrow());
                CaptchaChallengeResponse firstResponse = first.get(5, TimeUnit.SECONDS);
                CaptchaChallengeResponse secondResponse = second.get(5, TimeUnit.SECONDS);
                assertThat(firstResponse.challengeId()).isNotEqualTo(secondResponse.challengeId());
                refreshed = service.issue(1L, "user01", "active-token").orElseThrow();
                assertThat(refreshed).isIn(firstResponse, secondResponse);
            }
            assertThat(refreshed.challengeId()).isNotEqualTo(initial.challengeId());
            assertThat(refreshed.imageBase64()).isNotEqualTo(initial.imageBase64());
            assertThat(refreshed.expiresAt()).isAfter(initial.expiresAt());
            assertThat(redis.opsForList().size(CaptchaImagePool.KEY)).isEqualTo(6);

            CaptchaChallengeResponse current = refreshed;
            for (int i = 0; i < 3; i++) {
                current = service.refresh(1L, "user01", "active-token").orElseThrow();
            }
            assertThatThrownBy(() -> service.refresh(1L, "user01", "active-token"))
                    .isInstanceOf(CaptchaRefreshRateLimitedException.class);
            assertThat(service.issue(1L, "user01", "active-token")).contains(current);
            assertThat(redis.opsForList().size(CaptchaImagePool.KEY)).isEqualTo(3);

            String issuedKey = CaptchaChallengeService.issuedKey(1L, "user01", "active-token");
            redis.expire(issuedKey, 0, TimeUnit.SECONDS);
            assertThatThrownBy(() -> service.refresh(1L, "user01", "active-token"))
                    .isInstanceOf(CaptchaChallengeExpiredException.class);
            assertThat(redis.opsForList().size(CaptchaImagePool.KEY)).isEqualTo(3);

            service.issue(1L, "user01", "active-token").orElseThrow();
            assertThatThrownBy(() -> service.refresh(1L, "user01", "active-token"))
                    .isInstanceOf(CaptchaRefreshRateLimitedException.class);
        } finally {
            connection.destroy();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TICKET_CAPTCHA_TEST_REDIS_PORT", matches = "\\d+")
    @SuppressWarnings("unchecked")
    void verify_only_accepts_the_current_challenge_once_and_retains_attempts_across_refresh() throws Exception {
        LettuceConnectionFactory connection = openConnection(4);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connection);
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            new CaptchaImagePool(redis, mapper, new CaptchaImageGenerator(), new CaptchaPoolProperties(4, 4)).refill();

            EventRepository events = mock(EventRepository.class);
            given(events.selectById(1L)).willReturn(Event.builder().captchaEnabled(true).build());
            QueueAccessService queue = mock(QueueAccessService.class);
            Instant activeExpiry = Instant.now().plusSeconds(90);
            given(queue.validateActiveToken(1L, "user01", "active-one")).willReturn(activeExpiry);
            given(queue.validateActiveToken(1L, "user02", "active-two")).willReturn(activeExpiry);
            KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
            String detectionTopic = "ticketing-detection-log";
            given(kafka.send(eq(detectionTopic), anyString(), anyString())).willReturn(CompletableFuture.completedFuture(null));
            Tracer tracer = mock(Tracer.class);
            Span span = mock(Span.class);
            TraceContext traceContext = mock(TraceContext.class);
            String traceId = "ab".repeat(16);
            given(tracer.currentSpan()).willReturn(span);
            given(span.context()).willReturn(traceContext);
            given(traceContext.traceId()).willReturn(traceId);
            ConcurrentLinkedQueue<Runnable> pendingPublications = new ConcurrentLinkedQueue<>();
            TicketingDetectionLogProducer producer = new TicketingDetectionLogProducer(
                    kafka, mapper, pendingPublications::add, mock(TaskScheduler.class), detectionTopic);
            CaptchaChallengeService service = new CaptchaChallengeService(events, queue, redis, mapper, producer,
                    Optional.of(tracer));

            String firstKey = CaptchaChallengeService.issuedKey(1L, "user01", "active-one");
            CaptchaChallengeResponse oldChallenge = service.issue(1L, "user01", "active-one").orElseThrow();
            String oldAnswer = mapper.readTree(redis.opsForValue().get(firstKey)).get("answer").asText();
            CaptchaChallengeResponse current = service.refresh(1L, "user01", "active-one").orElseThrow();
            assertThatThrownBy(() -> service.verify(1L, "user01", "active-one", oldChallenge.challengeId(), oldAnswer))
                    .isInstanceOf(CaptchaChallengeChangedException.class);
            assertThat(redis.hasKey(firstKey + ":attempts")).isFalse();

            String answer = mapper.readTree(redis.opsForValue().get(firstKey)).get("answer").asText();
            Callable<Boolean> verify = () -> {
                try {
                    return service.verify(1L, "user01", "active-one", current.challengeId(), answer).passed();
                } catch (CaptchaChallengeExpiredException e) {
                    return false;
                }
            };
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<Boolean> first = executor.submit(verify);
                Future<Boolean> second = executor.submit(verify);
                assertThat(new Boolean[] {first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)})
                        .containsExactlyInAnyOrder(true, false);
            }
            assertThat(redis.hasKey(firstKey)).isFalse();
            assertThat(redis.opsForValue().get(firstKey + ":passed")).isEqualTo(current.challengeId().toString());
            assertThat(redis.getExpire(firstKey + ":passed", TimeUnit.MILLISECONDS))
                    .isCloseTo(activeExpiry.toEpochMilli() - System.currentTimeMillis(), org.assertj.core.data.Offset.offset(1000L));
            assertThat(service.issue(1L, "user01", "active-one")).isEmpty();
            assertThat(service.refresh(1L, "user01", "active-one")).isEmpty();
            long passedTtl = redis.getExpire(firstKey + ":passed", TimeUnit.MILLISECONDS);
            service.requirePassed(1L, "user01", "active-one");
            service.requirePassed(1L, "user01", "active-one");
            assertThat(redis.getExpire(firstKey + ":passed", TimeUnit.MILLISECONDS)).isBetween(1L, passedTtl);
            assertThatThrownBy(() -> service.requirePassed(1L, "user01", "different-token"))
                    .isInstanceOf(CaptchaRequiredException.class);
            service.closeSession(1L, "user01", "active-one");
            assertThat(redis.hasKey(firstKey + ":passed")).isFalse();
            assertThatThrownBy(() -> service.requirePassed(1L, "user01", "active-one"))
                    .isInstanceOf(CaptchaRequiredException.class);

            CaptchaChallengeResponse secondUser = service.issue(1L, "user02", "active-two").orElseThrow();
            for (int i = 0; i < 4; i++) {
                assertThat(service.verify(1L, "user02", "active-two", secondUser.challengeId(), "wrong").passed())
                        .isFalse();
            }
            verifyNoInteractions(kafka);
            CaptchaChallengeResponse refreshed = service.refresh(1L, "user02", "active-two").orElseThrow();
            Instant beforeDetection = Instant.now();
            Runnable verifyWrongAnswer = () -> assertThatThrownBy(() -> service.verify(
                    1L, "user02", "active-two", refreshed.challengeId(), "wrong"))
                    .isInstanceOf(CaptchaAttemptLimitException.class);
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<?> first = executor.submit(verifyWrongAnswer);
                Future<?> second = executor.submit(verifyWrongAnswer);
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            }
            String secondKey = CaptchaChallengeService.issuedKey(1L, "user02", "active-two");
            String secondAnswer = mapper.readTree(redis.opsForValue().get(secondKey)).get("answer").asText();
            assertThatThrownBy(() -> service.verify(1L, "user02", "active-two", refreshed.challengeId(), secondAnswer))
                    .isInstanceOf(CaptchaAttemptLimitException.class);
            assertThat(redis.hasKey(secondKey + ":passed")).isFalse();
            redis.delete(secondKey);
            assertThatThrownBy(() -> service.verify(1L, "user02", "active-two", refreshed.challengeId(), "wrong"))
                    .isInstanceOf(CaptchaAttemptLimitException.class);

            assertThat(pendingPublications).hasSize(1);
            given(tracer.currentSpan()).willReturn(null);
            pendingPublications.remove().run();
            ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
            String sessionKeyHash = secondKey.substring(secondKey.lastIndexOf(':') + 1);
            verify(kafka).send(eq(detectionTopic), eq(sessionKeyHash), payload.capture());
            TicketingDetectionLogEvent detection = mapper.readValue(payload.getValue(), TicketingDetectionLogEvent.class);
            assertThat(detection.getEventId()).isEqualTo(1L);
            assertThat(detection.getUserId()).isEqualTo("user02");
            assertThat(detection.getSessionKeyHash()).matches("[0-9a-f]{64}").isEqualTo(sessionKeyHash);
            assertThat(detection.getDetectionType().name()).isEqualTo("CAPTCHA_ATTEMPT_LIMIT_REACHED");
            assertThat(detection.getTraceId()).isEqualTo(traceId);
            assertThat(detection.getOccurredAt()).isBetween(beforeDetection, Instant.now());
            assertThat(detection.getEvidence()).containsOnlyKeys("attempts").containsEntry("attempts", 5);
            assertThat(payload.getValue()).doesNotContain("active-two", "\"answer\"");
        } finally {
            connection.destroy();
        }
    }

    private static LettuceConnectionFactory openConnection(int database) {
        int port = Integer.parseInt(System.getenv("TICKET_CAPTCHA_TEST_REDIS_PORT"));
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", port);
        config.setDatabase(database);
        LettuceConnectionFactory connection = new LettuceConnectionFactory(config);
        connection.afterPropertiesSet();
        return connection;
    }
}
