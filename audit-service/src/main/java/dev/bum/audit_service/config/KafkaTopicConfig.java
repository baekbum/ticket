package dev.bum.audit_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final String DLT_SUFFIX = ".DLT";
    private static final String DLT_RETENTION_MS = "1209600000";
    private static final String DLT_RETENTION_BYTES = "1073741824";
    @Value("${topic.audit.log.name}")
    private String auditLogTopicName;
    @Value("${topic.login.log.name}")
    private String loginLogTopicName;
    @Value("${topic.ticketing.detection.log.name}")
    private String ticketingDetectionLogTopicName;

    @Bean
    public NewTopic auditLogTopic() {

        return topicBuilder(auditLogTopicName).build();
    }

    @Bean
    public NewTopic auditLogDltTopic() {

        return dltTopic(auditLogTopicName);
    }

    @Bean
    public NewTopic loginLogDltTopic() {

        return dltTopic(loginLogTopicName);
    }

    @Bean
    public NewTopic ticketingDetectionLogTopic() {

        return topicBuilder(ticketingDetectionLogTopicName).build();
    }

    @Bean
    public NewTopic ticketingDetectionLogDltTopic() {

        return dltTopic(ticketingDetectionLogTopicName);
    }

    private TopicBuilder topicBuilder(String topicName) {

        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1);
    }

    private NewTopic dltTopic(String topicName) {

        return topicBuilder(topicName + DLT_SUFFIX)
                .config(TopicConfig.RETENTION_MS_CONFIG, DLT_RETENTION_MS)
                .config(TopicConfig.RETENTION_BYTES_CONFIG, DLT_RETENTION_BYTES)
                .build();
    }
}
