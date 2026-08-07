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

    @Bean
    public NewTopic auditLogTopic() {
        return TopicBuilder.name(auditLogTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditLogDltTopic() {
        return TopicBuilder.name(auditLogTopicName + DLT_SUFFIX)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, DLT_RETENTION_MS)
                .config(TopicConfig.RETENTION_BYTES_CONFIG, DLT_RETENTION_BYTES)
                .build();
    }
}
