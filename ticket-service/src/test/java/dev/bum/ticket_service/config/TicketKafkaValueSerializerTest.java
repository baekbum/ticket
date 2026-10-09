package dev.bum.ticket_service.config;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TicketKafkaValueSerializerTest {
    private final TicketKafkaValueSerializer serializer = new TicketKafkaValueSerializer();

    @ParameterizedTest
    @ValueSource(strings = {"application.yaml", "application-prod.yaml"})
    void bothProfilesUseSerializerSupportingDlt(String resource) {
        var yaml = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        yaml.setResources(new org.springframework.core.io.ClassPathResource(resource));
        assertThat(yaml.getObject().getProperty("spring.kafka.producer.value-serializer"))
                .isEqualTo(TicketKafkaValueSerializer.class.getName());
    }

    @Test
    void paymentCompletedJsonIsNotDoubleEncoded() {
        String payload = "{\"paymentNo\":\"PAY-test\",\"amount\":180000}";
        assertThat(serializer.serialize("payment-completed", payload))
                .isEqualTo(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void deserializationFailureDltPreservesRawBytes() {
        byte[] payload = new byte[] {'{', (byte) 0xff, 'x'};
        assertThat(serializer.serialize("virtual-account-expired.DLT", new RecordHeaders(), payload))
                .isEqualTo(payload);
    }

    @Test
    void businessFailureDltSerializesEventAsJson() {
        var event = VirtualAccountExpiredEvent.builder().paymentNo("PAY-test").build();
        var headers = new RecordHeaders();
        byte[] payload = serializer.serialize("virtual-account-expired.DLT", headers, event);
        assertThat(new JsonDeserializer<>(VirtualAccountExpiredEvent.class, false)
                .deserialize("virtual-account-expired.DLT", payload)).isEqualTo(event);
        assertThat(headers.lastHeader("__TypeId__")).isNull();
    }
}
