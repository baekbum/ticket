package dev.bum.ticket_service.config;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketKafkaValueDeserializerTest {

    private final TicketKafkaValueDeserializer deserializer = new TicketKafkaValueDeserializer(Map.of(
            "user-created", UserCreated.class,
            "payment-completed", PaymentCompleted.class
    ));

    @Test
    void choosesDifferentEventTypesByTopicOnSameDeserializer() {
        byte[] user = json("{\"userId\":\"USER-1\"}");
        byte[] payment = json("{\"paymentNo\":\"PAY-1\",\"amount\":180000}");
        assertThat(deserializer.deserialize("user-created", new RecordHeaders(), user))
                .isEqualTo(new UserCreated("USER-1"));
        assertThat(deserializer.deserialize("payment-completed", new RecordHeaders(), payment))
                .isEqualTo(new PaymentCompleted("PAY-1", 180000));
        assertThat(deserializer.deserialize("user-created", new RecordHeaders(), user))
                .isEqualTo(new UserCreated("USER-1"));
    }

    @Test
    void ignoresProducerTypeHeaderAndUsesTopicContract() {
        var headers = new RecordHeaders();
        headers.add("__TypeId__", json(PaymentCompleted.class.getName()));
        assertThat(deserializer.deserialize("user-created", headers, json("{\"userId\":\"USER-1\"}")))
                .isEqualTo(new UserCreated("USER-1"));
    }

    @Test
    void rejectsUnregisteredTopicInsteadOfFallingBackToMapOrProducerType() {
        var headers = new RecordHeaders();
        headers.add("__TypeId__", json(UserCreated.class.getName()));
        assertThatThrownBy(() -> deserializer.deserialize("unknown", headers, json("{}")))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("No event type registered for Kafka topic: unknown");
    }

    @Test
    void unregisteredTopicRetainsOriginalBytesInErrorHandlingWrapper() {
        var wrapper = new ErrorHandlingDeserializer<>(deserializer);
        wrapper.configure(Map.of(), false);
        var headers = new RecordHeaders();
        byte[] payload = json("{\"userId\":\"USER-1\"}");
        assertThat(wrapper.deserialize("unknown", headers, payload)).isNull();
        var record = new ConsumerRecord<String, Object>("unknown", 0, 0, "USER-1", null);
        headers.forEach(record.headers()::add);
        var exception = SerializationUtils.getExceptionFromHeader(record,
                SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER,
                new LogAccessor(TicketKafkaValueDeserializerTest.class));
        assertThat(exception).isNotNull();
        assertThat(exception.getData()).isEqualTo(payload);
    }

    @Test
    void preservesNullValueForTombstone() {
        assertThat(deserializer.deserialize("user-created", new RecordHeaders(), (byte[]) null)).isNull();
    }

    private static byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public record UserCreated(String userId) {}
    public record PaymentCompleted(String paymentNo, int amount) {}
}
