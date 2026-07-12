package dev.bum.ticket_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${topic.reservation.name}")
    private String reservationTopicName;

    @Bean
    public NewTopic reservationTopic() {
        return TopicBuilder.name(reservationTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reservationDltTopic() {
        return TopicBuilder.name(reservationTopicName + ".DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
