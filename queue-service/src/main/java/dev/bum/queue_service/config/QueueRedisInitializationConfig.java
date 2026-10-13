package dev.bum.queue_service.config;

import dev.bum.queue_service.service.QueueRedisScripts;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration(proxyBeanMethods = false)
public class QueueRedisInitializationConfig {
    @Bean
    static BeanPostProcessor eagerRedisConnections() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof LettuceConnectionFactory factory) {
                    // Reactive API도 첫 공유 연결 생성은 블로킹이므로 서버 시작 전에 준비한다.
                    factory.setEagerInitialization(true);
                    // 관측 빈 생성 중의 순환 대기를 피하고 서버 시작 전 Lifecycle 단계에서 연결한다.
                    factory.setEarlyStartup(false);
                }
                return bean;
            }
        };
    }

    @Bean
    InitializingBean queueRedisScripts() {
        return () -> {
            // 파일 읽기와 SHA 계산을 요청 이벤트 루프에서 수행하지 않는다.
            QueueRedisScripts.REGISTER_WAITING.getSha1();
            QueueRedisScripts.REFRESH_WAITING.getSha1();
            QueueRedisScripts.STATUS_WITH_IDEMPOTENCY.getSha1();
            QueueRedisScripts.REMOVE_TOKEN.getSha1();
            QueueRedisScripts.CLEAR_EVENT.getSha1();
        };
    }
}