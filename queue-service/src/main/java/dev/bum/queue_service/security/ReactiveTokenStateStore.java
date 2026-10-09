package dev.bum.queue_service.security;

import dev.bum.common.security.TokenState;
import dev.bum.common.security.TokenStateStore;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/** 인증 Redis는 대기열 DB와 분리된 전용 연결을 사용한다. */
public final class ReactiveTokenStateStore implements AutoCloseable {
    private final LettuceConnectionFactory connectionFactory;
    private final ReactiveStringRedisTemplate redis;

    public ReactiveTokenStateStore(LettuceConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        this.redis = new ReactiveStringRedisTemplate(connectionFactory);
    }

    public Mono<TokenState> get(String userId) {
        return redis.opsForValue().get(TokenStateStore.key(userId)).map(TokenState::decode);
    }

    @Override
    public void close() {
        connectionFactory.destroy();
    }
}
