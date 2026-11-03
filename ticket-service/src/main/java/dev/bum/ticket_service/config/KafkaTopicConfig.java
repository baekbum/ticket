package dev.bum.ticket_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic eventQueuePolicyTopic(@Value("${topic.event-policy.name:event-queue-policy}") String name) {

        return TopicBuilder.name(name).partitions(3).replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT).build();
    }

    private static final String DLT_SUFFIX = ".DLT";
    private static final String DLT_RETENTION_MS = "1209600000";
    private static final String DLT_RETENTION_BYTES = "1073741824";
    @Value("${topic.payment.completed.name}")
    private String paymentCompletedTopicName;
    @Value("${topic.payment.virtual-account.expired.name}")
    private String virtualAccountExpiredTopicName;

    @Bean
    public NewTopic paymentCompletedTopic() {

        return TopicBuilder.name(paymentCompletedTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentCompletedDltTopic() {

        return TopicBuilder.name(paymentCompletedTopicName + DLT_SUFFIX)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, DLT_RETENTION_MS)
                .config(TopicConfig.RETENTION_BYTES_CONFIG, DLT_RETENTION_BYTES)
                .build();
    }

    @Bean
    public NewTopic virtualAccountExpiredTopic() {

        return TopicBuilder.name(virtualAccountExpiredTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic virtualAccountExpiredDltTopic() {

        return TopicBuilder.name(virtualAccountExpiredTopicName + DLT_SUFFIX)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, DLT_RETENTION_MS)
                .config(TopicConfig.RETENTION_BYTES_CONFIG, DLT_RETENTION_BYTES)
                .build();
    }
}
