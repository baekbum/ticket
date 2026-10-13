package dev.bum.queue_service.service.reactive;

import java.time.Duration;

final class ReactiveQueueRedisSupport {
    private ReactiveQueueRedisSupport() {
    }

    static long ttlSeconds(Duration ttl) {
        // 존재하지 않는 키(-2)와 만료 없는 키(-1)의 Redis 반환값을 그대로 유지한다.
        if (ttl.isNegative() && ttl.toMillis() > -1000) {
            return ttl.toMillis();
        }
        return ttl.toSeconds();
    }
}
