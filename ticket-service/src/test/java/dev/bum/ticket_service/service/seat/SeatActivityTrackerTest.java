package dev.bum.ticket_service.service.seat;

import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException;
import dev.bum.ticket_service.kafka.ticketing.TicketingDetectionLogProducer;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.queue.QueueAccessService.ActiveSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@EnabledIfEnvironmentVariable(named = "TICKET_CAPTCHA_TEST_REDIS_PORT", matches = "\\d+")
class SeatActivityTrackerTest {

    @Test
    void tracks_query_windows_occupation_attempts_and_the_first_prepare_elapsed_time() {
        int port = Integer.parseInt(System.getenv("TICKET_CAPTCHA_TEST_REDIS_PORT"));
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", port);
        config.setDatabase(5);
        LettuceConnectionFactory connection = new LettuceConnectionFactory(config);
        connection.afterPropertiesSet();

        try {
            StringRedisTemplate redis = new StringRedisTemplate(connection);
            HashOperations<String, String, String> hashes = redis.opsForHash();
            Instant expiresAt = Instant.now().plusSeconds(120);
            Instant issuedAt = expiresAt.minusSeconds(130);
            ActiveSession session = new ActiveSession(issuedAt, expiresAt);
            QueueAccessService queue = mock(QueueAccessService.class);
            given(queue.validate(1L, "buyer", "active")).willReturn(session);
            given(queue.validate(1L, "buyer", "invalid")).willThrow(new ActiveTokenExpiredException());
            TicketingDetectionLogProducer producer = mock(TicketingDetectionLogProducer.class);
            SeatActivityTracker tracker = new SeatActivityTracker(redis, queue, producer);
            String key = SeatActivityTracker.activityKey(1L, "buyer", "active");

            tracker.recordSeatQuery(1L, "buyer", "invalid");
            assertThat(redis.hasKey(SeatActivityTracker.activityKey(1L, "buyer", "invalid"))).isFalse();
            tracker.recordSeatQuery(1L, "buyer", "active");
            Instant firstQuery = Instant.ofEpochMilli(Long.parseLong(hashes.get(key, "firstSeatQueryAt")));
            tracker.recordCaptchaPassed(1L, "buyer", "active", session);
            hashes.put(key, "seatQueryWindow", "0");
            hashes.put(key, "seatQueryCount", "15");
            tracker.recordSeatQuery(1L, "buyer", "active");
            tracker.recordSeatOccupation(1L, "buyer", "active", session);
            tracker.recordSeatOccupation(1L, "buyer", "active", session);
            tracker.recordSeatOccupied(1L, "buyer", "active", session);
            Instant prepareRequestedAt = Instant.now();
            tracker.recordPrepare(1L, "buyer", "active", prepareRequestedAt, session);
            tracker.recordPrepare(1L, "buyer", "active", prepareRequestedAt.plusMillis(400), session);

            long elapsed = prepareRequestedAt.toEpochMilli() - issuedAt.toEpochMilli();
            verify(queue).validate(1L, "buyer", "active");
            verify(queue).validate(1L, "buyer", "invalid");
            assertThat(hashes.entries(key)).containsEntry("seatQueryCount", "1")
                    .containsEntry("firstSeatQueryAt", Long.toString(firstQuery.toEpochMilli()))
                    .containsEntry("activeTokenIssuedAt", Long.toString(issuedAt.toEpochMilli()))
                    .containsKeys("captchaPassedAt", "firstSeatOccupiedAt")
                    .containsEntry("seatOccupyAttempts", "2")
                    .containsEntry("firstPrepareRequestedAt", Long.toString(prepareRequestedAt.toEpochMilli()))
                    .containsEntry("firstPrepareElapsedMs", Long.toString(elapsed));
            assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isCloseTo(
                    expiresAt.toEpochMilli() - System.currentTimeMillis(), offset(1000L));

            String legacyKey = SeatActivityTracker.activityKey(1L, "buyer", "legacy");
            hashes.put(legacyKey, "firstPrepareElapsedMs", "400");
            tracker.recordPrepare(1L, "buyer", "legacy", prepareRequestedAt, new ActiveSession(null, expiresAt));
            assertThat(hashes.entries(legacyKey)).containsKey("firstPrepareRequestedAt").doesNotContainKey("firstPrepareElapsedMs");

            String burstKey = SeatActivityTracker.activityKey(1L, "buyer", "burst");
            tracker.recordAreaLayoutQuery(1L, "buyer", "burst", session);
            for (int i = 0; i < 59; i++) tracker.recordAreaLayoutQuery(1L, "buyer", "burst", session);
            assertThat(hashes.get(burstKey, "areaLayoutRefreshCount")).isEqualTo("59");
            verifyNoInteractions(producer);

            tracker.recordAreaLayoutQuery(1L, "buyer", "burst", session);
            tracker.recordAreaLayoutQuery(1L, "buyer", "burst", session);
            verify(producer).report(eq(1L), eq("buyer"), eq("burst"), eq(TicketingDetectionType.AREA_LAYOUT_REFRESH_LIMIT_REACHED),
                    any(Instant.class), eq(Map.of("refreshCount", 60L, "windowMs", 60_000L)));
            assertThat(hashes.get(burstKey, "areaLayoutRefreshCount")).isEqualTo("61");

            Instant exactLimit = issuedAt.plusMillis(5000);
            for (String token : new String[]{"fast", "slow", "no-captcha"}) {
                String activityKey = SeatActivityTracker.activityKey(1L, "buyer", token);
                if (!token.equals("no-captcha")) hashes.put(activityKey, "captchaPassedAt", Long.toString(issuedAt.plusMillis(1000).toEpochMilli()));
                hashes.put(activityKey, "firstSeatOccupiedAt", Long.toString(issuedAt.plusMillis(2000).toEpochMilli()));
                tracker.recordPrepare(1L, "buyer", token, token.equals("slow") ? exactLimit.plusMillis(1) : exactLimit, session);
            }
            tracker.recordPrepare(1L, "buyer", "fast", exactLimit, session);
            verify(producer).report(1L, "buyer", "fast", TicketingDetectionType.PREPARE_TOO_FAST, exactLimit,
                    Map.of("elapsedMs", 5000L, "limitMs", 5000L));
            verifyNoMoreInteractions(producer);
        } finally {
            connection.destroy();
        }
    }
}
