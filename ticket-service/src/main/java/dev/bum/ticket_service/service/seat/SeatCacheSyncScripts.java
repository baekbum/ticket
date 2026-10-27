package dev.bum.ticket_service.service.seat;

import org.springframework.data.redis.core.script.DefaultRedisScript;

final class SeatCacheSyncScripts {
    private SeatCacheSyncScripts() { }

    // 값 갱신과 잠금 검사를 원자적으로 수행하고, 다른 예매의 새 선점은 해제하지 않는다.
    static final DefaultRedisScript<Long> APPLY = new DefaultRedisScript<>("""
            local current = redis.call('get', KEYS[1])
            local lock = redis.call('get', KEYS[2])
            if (lock and lock ~= ARGV[3])
                or (current and string.sub(current, 1, 7) == 'LOCKED:' and current ~= ARGV[3]) then
                return 0
            end
            redis.call('psetex', KEYS[1], ARGV[1], ARGV[2])
            redis.call('del', KEYS[2])
            return 1
            """, Long.class);
}
