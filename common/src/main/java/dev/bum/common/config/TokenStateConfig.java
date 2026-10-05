package dev.bum.common.config;

import dev.bum.common.security.RedisTokenStateStore;
import dev.bum.common.security.TokenStateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class TokenStateConfig {
    // RedisConnectionFactory 빈을 추가하면 각 서비스의 기존 좌석/대기열 Redis 설정을
    // 대체하므로 인증 저장소의 연결은 이 빈에서만 관리한다.
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(TokenStateStore.class)
    public ManagedTokenStateStore tokenStateStore(
            @Value("${token-state.redis.host:${TOKEN_STATE_REDIS_HOST:localhost}}") String host,
            @Value("${token-state.redis.port:${TOKEN_STATE_REDIS_PORT:6390}}") int port,
            @Value("${token-state.redis.database:${TOKEN_STATE_REDIS_DATABASE:0}}") int database,
            @Value("${token-state.redis.password:${TOKEN_STATE_REDIS_PASSWORD:}}") String password) {

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        configuration.setDatabase(database);

        if (!password.isBlank()) configuration.setPassword(password);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration,
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2))
                        .shutdownTimeout(Duration.ZERO).build());

        factory.afterPropertiesSet();

        return new ManagedTokenStateStore(factory);
    }

    public static final class ManagedTokenStateStore extends RedisTokenStateStore implements AutoCloseable {
        private final LettuceConnectionFactory factory;

        ManagedTokenStateStore(LettuceConnectionFactory factory) {
            super(new StringRedisTemplate(factory));
            this.factory = factory;
        }

        @Override
        public void close() {
            factory.destroy();
        }
    }
}
