package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueRedisEntryResponse;
import dev.bum.common.service.queue.dto.QueueRedisInspectResponse;
import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QueueRedisInspectService {

    private final StringRedisTemplate redisTemplate;
    private final QueueRedisKeys keys;
    private final UserQueueSessionService userQueueSessionService;

    public QueueRedisInspectResponse inspectEventQueue(Long eventId, QueueRedisInspectMode mode, int limit) {
        QueueRedisInspectMode inspectMode = mode == null ? QueueRedisInspectMode.WAITING : mode;
        int normalizedLimit = normalizeLimit(limit);

        if (inspectMode == QueueRedisInspectMode.ACTIVE) {
            return inspectActive(eventId, normalizedLimit);
        }

        return inspectWaiting(eventId, normalizedLimit);
    }

    public QueueRedisInspectResponse inspectToken(String token) {
        String key = activeTokenKey(token);
        String value = redisTemplate.opsForValue().get(key);
        Long ttlSeconds = redisTemplate.getExpire(key);

        QueueRedisEntryResponse entry = QueueRedisEntryResponse.builder()
                .key(key)
                .token(token)
                .member(token)
                .value(value)
                .ttlSeconds(ttlSeconds)
                .build();

        return QueueRedisInspectResponse.builder()
                .mode(QueueRedisInspectMode.TOKEN)
                .token(token)
                .limit(1)
                .count(1)
                .entries(List.of(entry))
                .build();
    }

    public boolean removeToken(Long eventId, String token, QueueRedisInspectMode mode) {
        if (!StringUtils.hasText(token) || mode == null || mode == QueueRedisInspectMode.TOKEN) {
            return false;
        }

        if (mode == QueueRedisInspectMode.ACTIVE) {
            return removeActiveToken(eventId, token);
        }

        return removeWaitingToken(eventId, token);
    }

    private QueueRedisInspectResponse inspectWaiting(Long eventId, int limit) {
        String key = waitingKey(eventId);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .rangeWithScores(key, 0, limit - 1);

        List<QueueRedisEntryResponse> entries = new ArrayList<>();
        long rank = 1;
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                Double score = tuple.getScore();
                entries.add(QueueRedisEntryResponse.builder()
                        .key(key)
                        .member(tuple.getValue())
                        .token(tuple.getValue())
                        .rank(rank++)
                        .score(score)
                        .timestampMillis(score == null ? null : score.longValue())
                        .value(redisTemplate.opsForValue().get(keys.waitingTokenKey(tuple.getValue())))
                        .ttlSeconds(redisTemplate.getExpire(keys.waitingTokenKey(tuple.getValue())))
                        .build());
            }
        }

        return QueueRedisInspectResponse.builder()
                .mode(QueueRedisInspectMode.WAITING)
                .eventId(eventId)
                .limit(limit)
                .count(entries.size())
                .entries(entries)
                .build();
    }

    private QueueRedisInspectResponse inspectActive(Long eventId, int limit) {
        String key = activeKey(eventId);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .rangeWithScores(key, 0, limit - 1);

        List<QueueRedisEntryResponse> entries = new ArrayList<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String token = tuple.getValue();
                Double score = tuple.getScore();
                String activeTokenKey = activeTokenKey(token);
                entries.add(QueueRedisEntryResponse.builder()
                        .key(key)
                        .member(token)
                        .token(token)
                        .score(score)
                        .timestampMillis(score == null ? null : score.longValue())
                        .value(redisTemplate.opsForValue().get(activeTokenKey))
                        .ttlSeconds(redisTemplate.getExpire(activeTokenKey))
                        .build());
            }
        }

        return QueueRedisInspectResponse.builder()
                .mode(QueueRedisInspectMode.ACTIVE)
                .eventId(eventId)
                .limit(limit)
                .count(entries.size())
                .entries(entries)
                .build();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
    }

    private String waitingKey(Long eventId) {
        return "queue:event:" + eventId + ":waiting";
    }

    private String activeKey(Long eventId) {
        return "queue:event:" + eventId + ":active";
    }

    private String activeTokenKey(String token) {
        return "queue:active-token:" + token;
    }

    private boolean removeWaitingToken(Long eventId, String token) {
        String tokenValue = redisTemplate.opsForValue().get(keys.waitingTokenKey(token));
        Long removedFromWaiting = redisTemplate.opsForZSet().remove(keys.waitingKey(eventId), token);
        Long removedFromExpiry = redisTemplate.opsForZSet().remove(keys.waitingExpiryKey(eventId), token);
        Boolean removedTokenKey = redisTemplate.delete(keys.waitingTokenKey(token));
        removeWaitingUserSession(tokenValue, token);
        return positive(removedFromWaiting) || positive(removedFromExpiry) || Boolean.TRUE.equals(removedTokenKey);
    }

    private boolean removeActiveToken(Long eventId, String token) {
        String tokenValue = redisTemplate.opsForValue().get(keys.activeTokenKey(token));
        Long removedFromActive = redisTemplate.opsForZSet().remove(keys.activeKey(eventId), token);
        Boolean removedTokenKey = redisTemplate.delete(keys.activeTokenKey(token));
        removeActiveUserSession(tokenValue, token);
        return positive(removedFromActive) || Boolean.TRUE.equals(removedTokenKey);
    }

    private void removeWaitingUserSession(String tokenValue, String token) {
        String userId = userIdFromTokenValue(tokenValue);
        if (userId != null) {
            userQueueSessionService.removeWaiting(userId, token);
        }
    }

    private void removeActiveUserSession(String tokenValue, String token) {
        String userId = userIdFromTokenValue(tokenValue);
        if (userId != null) {
            userQueueSessionService.removeActive(userId, token);
        }
    }

    private String userIdFromTokenValue(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return null;
        }

        String[] parts = tokenValue.split(":", 2);
        return parts.length == 2 && StringUtils.hasText(parts[1]) ? parts[1] : null;
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }
}
