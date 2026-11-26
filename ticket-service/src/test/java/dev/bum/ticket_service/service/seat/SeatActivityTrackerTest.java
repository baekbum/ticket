package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.queue.QueueAccessService.ActiveSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
            Instant issuedAt = expiresAt.minusSeconds(125);
            ActiveSession session = new ActiveSession(issuedAt, expiresAt);
            QueueAccessService queue = mock(QueueAccessService.class);
            given(queue.validate(1L, "buyer", "active")).willReturn(session);
            given(queue.validate(1L, "buyer", "invalid")).willThrow(new ActiveTokenExpiredException());
            SeatActivityTracker tracker = new SeatActivityTracker(redis, queue);
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
        } finally {
            connection.destroy();
        }
    }
}
