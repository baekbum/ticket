package dev.bum.ticket_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${topic.payment.completed.name}")
    private String paymentCompletedTopicName;

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(paymentCompletedTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
