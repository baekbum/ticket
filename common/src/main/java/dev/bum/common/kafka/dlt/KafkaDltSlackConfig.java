package dev.bum.common.kafka.dlt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KafkaDltSlackProperties.class)
public class KafkaDltSlackConfig {
}
