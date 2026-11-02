package dev.bum.queue_service;

import dev.bum.common.kafka.event.EventQueuePolicyEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.Map;

public final class QueuePolicyFixture {
    private QueuePolicyFixture() {}
    public static EventQueuePolicyEvent event(long id, int capacity) {
        long now = System.currentTimeMillis();
        return new EventQueuePolicyEvent(id, 1, "ON_SALE", now - 60000, now + 3600000, now + 7200000, capacity);
    }
    public static void seed(StringRedisTemplate redis, long id, int capacity) {
        EventQueuePolicyEvent e = event(id, capacity);
        String key = "queue:event:" + id + ":policy";
        redis.opsForHash().putAll(key, Map.of("status", e.status(), "saleStartAt", Long.toString(e.saleStartAt()),
                "saleEndAt", Long.toString(e.saleEndAt()), "eventDateTime", Long.toString(e.eventDateTime()),
                "admissionSize", Integer.toString(capacity), "version", "1"));
        redis.expire(key, Duration.ofHours(1));
    }
}
