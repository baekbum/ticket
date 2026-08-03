package dev.bum.auth_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final String DLT_SUFFIX = ".DLT";

    @Value("${topic.name}")
    private String userEventTopicName;

    @Bean
    public NewTopic userEventDltTopic() {
        return TopicBuilder.name(userEventTopicName + DLT_SUFFIX)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
