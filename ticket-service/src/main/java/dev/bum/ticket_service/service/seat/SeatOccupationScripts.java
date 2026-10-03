package dev.bum.ticket_service.service.seat;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 좌석 상태와 선점 잠금을 함께 변경하는 Redis 스크립트. */
final class SeatOccupationScripts {
    private SeatOccupationScripts() {}

    // KEYS: 좌석 상태, 선점 잠금. ARGV: 잠금 값, 선점 TTL, DB 검증 후 캐시 누락 허용 여부.
    // 반환값: 1=성공, 0=선점 불가, -1=DB 검증 필요.
    static final DefaultRedisScript<Long> OCCUPY = new DefaultRedisScript<>("""
            local state = redis.call('get', KEYS[1])
            if redis.call('exists', KEYS[2]) == 1 then return 0 end
            if not state and ARGV[3] ~= '1' then return -1 end
            if state and state ~= 'AVAILABLE' then return 0 end
            redis.call('psetex', KEYS[2], ARGV[2], ARGV[1])
            redis.call('psetex', KEYS[1], ARGV[2], ARGV[1])
            return 1
            """, Long.class);

    // 이번 요청의 잠금만 해제하며, RESERVED나 다른 요청의 좌석 상태는 보존한다.
    static final DefaultRedisScript<Long> ROLLBACK = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[2]) ~= ARGV[1] then return 0 end
            if redis.call('get', KEYS[1]) == ARGV[1] then
                redis.call('psetex', KEYS[1], ARGV[2], 'AVAILABLE')
            end
            redis.call('del', KEYS[2])
            return 1
            """, Long.class);
}
