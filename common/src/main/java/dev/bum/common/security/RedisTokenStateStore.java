package dev.bum.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

public class RedisTokenStateStore implements TokenStateStore {
    // 같은 버전의 재전달은 세션을 유지한다. 이전 DB 스냅샷은 현재 상태를 덮어쓰지 않는다.
    private static final DefaultRedisScript<Long> PUBLISH = new DefaultRedisScript<>("""
            local current = redis.call('get', KEYS[1])
            if current then
                local version = tonumber(string.match(current, '^(%d+):'))
                if not version then return -1 end
                if version > tonumber(ARGV[1]) then return 0 end
                if version == tonumber(ARGV[1]) then
                    if current ~= ARGV[2] then return -1 end
                    return 1
                end
            end
            redis.call('set', KEYS[1], ARGV[2])
            redis.call('del', KEYS[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisTokenStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public TokenState get(String userId) {
        String value = redis.opsForValue().get(TokenStateStore.key(userId));
        return value == null ? null : TokenState.decode(value);
    }

    @Override
    public void publish(String userId, TokenState state) {
        Long result = redis.execute(PUBLISH, List.of(TokenStateStore.key(userId), "RT:" + userId),
                Long.toString(state.version()), state.encode());
        if (!Long.valueOf(1L).equals(result)) {
            throw new IllegalStateException("Token state publication rejected");
        }
    }
}
