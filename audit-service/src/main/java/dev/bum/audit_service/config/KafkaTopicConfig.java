package dev.bum.audit_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${topic.audit.log.name}")
    private String auditLogTopicName;

    @Bean
    public NewTopic auditLogTopic() {
        return TopicBuilder.name(auditLogTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
