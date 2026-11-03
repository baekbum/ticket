package dev.bum.queue_service.service.reactive;

import java.time.Duration;

final class ReactiveQueueRedisSupport {

    /**
     * 외부에서 인스턴스를 생성하지 못하도록 제한한다.
     */
    private ReactiveQueueRedisSupport() {
    }

    /**
     * 키의 남은 유효 시간을 초 단위로 조회한다.
     */
    static long ttlSeconds(Duration ttl) {

        // 존재하지 않는 키(-2)와 만료 없는 키(-1)의 Redis 반환값을 그대로 유지한다.
        if (ttl.isNegative() && ttl.toMillis() > -1000) {
            return ttl.toMillis();
        }
        return ttl.toSeconds();
    }
}
