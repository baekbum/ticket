package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueRedisEntryResponse;
import dev.bum.common.service.queue.dto.QueueRedisInspectResponse;
import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QueueRedisInspectService {

    private final StringRedisTemplate redisTemplate;

    public QueueRedisInspectResponse inspectEventQueue(Long eventId, QueueRedisInspectMode mode, int limit) {
        QueueRedisInspectMode inspectMode = mode == null ? QueueRedisInspectMode.WAITING : mode;
        int normalizedLimit = normalizeLimit(limit);

        if (inspectMode == QueueRedisInspectMode.ACTIVE) {
            return inspectActive(eventId, normalizedLimit);
        }

        return inspectWaiting(eventId, normalizedLimit);
    }

    public QueueRedisInspectResponse inspectToken(String token) {
        String key = tokenKey(token);
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
                        .rank(rank++)
                        .score(score)
                        .timestampMillis(score == null ? null : score.longValue())
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
                String tokenKey = tokenKey(token);
                entries.add(QueueRedisEntryResponse.builder()
                        .key(key)
                        .member(token)
                        .token(token)
                        .score(score)
                        .timestampMillis(score == null ? null : score.longValue())
                        .value(redisTemplate.opsForValue().get(tokenKey))
                        .ttlSeconds(redisTemplate.getExpire(tokenKey))
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

    private String tokenKey(String token) {
        return "queue:token:" + token;
    }
}
