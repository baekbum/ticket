package dev.bum.queue_service.config;

import dev.bum.queue_service.security.ReactiveTokenStateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class ReactiveTokenStateConfig {
    @Bean(destroyMethod = "close")
    public ReactiveTokenStateStore reactiveTokenStateStore(
            @Value("${token-state.redis.host:${TOKEN_STATE_REDIS_HOST:localhost}}") String host,
            @Value("${token-state.redis.port:${TOKEN_STATE_REDIS_PORT:6390}}") int port,
            @Value("${token-state.redis.database:${TOKEN_STATE_REDIS_DATABASE:0}}") int database,
            @Value("${token-state.redis.password:${TOKEN_STATE_REDIS_PASSWORD:}}") String password,
            @Value("${token-state.redis.timeout:${TOKEN_STATE_REDIS_TIMEOUT:2s}}") Duration timeout) {

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        configuration.setDatabase(database);

        if (!password.isBlank()) {
            configuration.setPassword(password);
        }

        // 인증용 연결을 빈으로 등록하면 Boot의 대기열 Redis 연결을 대체하므로 별도로 관리한다.
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration,
                LettuceClientConfiguration.builder()
                        .commandTimeout(timeout)
                        .shutdownTimeout(Duration.ZERO)
                        .build());
        // 첫 요청에서 동기 연결 수립이 발생하지 않도록 시작할 때 연결한다.
        factory.setEagerInitialization(true);
        factory.afterPropertiesSet();

        return new ReactiveTokenStateStore(factory);
    }
}
