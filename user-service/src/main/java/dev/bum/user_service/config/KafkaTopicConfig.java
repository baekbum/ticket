package dev.bum.user_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${topic.user.name}")
    private String userEventTopicName;

    @Bean
    public NewTopic userTopic() {
        return TopicBuilder.name(userEventTopicName)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
