package dev.bum.ticket_service.service.captcha;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.ticket_service.config.CaptchaPoolProperties;
import dev.bum.ticket_service.config.QueueAccessProperties;
import dev.bum.ticket_service.exception.captcha.CaptchaUnavailableException;
import dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException;
import dev.bum.ticket_service.feign.queue.QueueServiceClient;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CaptchaChallengeServiceTest {

    @Test
    void event_without_captcha_does_not_consume_a_prepared_image() {
        EventRepository events = mock(EventRepository.class);
        given(events.selectById(1L)).willReturn(Event.builder().captchaEnabled(false).build());
        QueueAccessService queue = mock(QueueAccessService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        var service = new CaptchaChallengeService(events, queue, redis, new ObjectMapper());

        assertThat(service.issue(1L, "user01", null)).isEmpty();
        verifyNoInteractions(queue, redis);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TICKET_CAPTCHA_TEST_REDIS_PORT", matches = "\\d+")
    void validates_tokens_and_reuses_one_issued_challenge_across_concurrent_requests() throws Exception {
        int port = Integer.parseInt(System.getenv("TICKET_CAPTCHA_TEST_REDIS_PORT"));
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", port);
        config.setDatabase(1);
        LettuceConnectionFactory connection = new LettuceConnectionFactory(config);
        connection.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connection);
            ObjectMapper mapper = new ObjectMapper();
            var pool = new CaptchaImagePool(redis, mapper, new CaptchaImageGenerator(), new CaptchaPoolProperties(2, 2));
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
            var service = new CaptchaChallengeService(events, new QueueAccessService(new QueueAccessProperties(), queue),
                    redis, mapper);

            assertThatThrownBy(() -> service.issue(1L, "user01", "invalid"))
                    .isInstanceOf(ActiveTokenExpiredException.class);
            assertThat(redis.opsForList().size(CaptchaImagePool.KEY)).isEqualTo(2);

            CaptchaChallengeResponse first;
            try (var executor = Executors.newFixedThreadPool(2)) {
                var a = executor.submit(() -> service.issue(1L, "user01", "active-one").orElseThrow());
                var b = executor.submit(() -> service.issue(1L, "user01", "active-one").orElseThrow());
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

            var second = service.issue(1L, "user02", "active-two").orElseThrow();
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
}
