package dev.bum.ticket_service.config;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TicketKafkaValueSerializerTest {
    private final TicketKafkaValueSerializer serializer = new TicketKafkaValueSerializer();

    @ParameterizedTest
    @ValueSource(strings = {"application.yaml", "application-prod.yaml"})
    void bothProfilesUseSerializerSupportingDlt(String resource) {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(resource));
        assertThat(yaml.getObject().getProperty("spring.kafka.producer.value-serializer"))
                .isEqualTo(TicketKafkaValueSerializer.class.getName());
    }

    @Test
    void ticketingDetectionJsonIsNotDoubleEncoded() {
        String payload = "{\"eventId\":101,\"detectionType\":\"CAPTCHA_ATTEMPT_LIMIT_REACHED\"}";
        assertThat(serializer.serialize("ticketing-detection-log", payload))
                .isEqualTo(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void deserializationFailureDltPreservesRawBytes() {
        byte[] payload = new byte[] {'{', (byte) 0xff, 'x'};
        assertThat(serializer.serialize("ticketing-detection-log.DLT", new RecordHeaders(), payload))
                .isEqualTo(payload);
    }

}
