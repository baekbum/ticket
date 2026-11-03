package dev.bum.queue_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class EventPolicyKafkaConfig {

    @Bean
    public DefaultErrorHandler eventPolicyErrorHandler() {

        // Redis 장애 중에는 소비 완료로 처리하지 않는다. 최신 스냅샷도 주기적으로 다시 발행된다.
        return new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
