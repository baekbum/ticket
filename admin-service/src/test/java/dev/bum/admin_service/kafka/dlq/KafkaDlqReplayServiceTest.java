package dev.bum.admin_service.kafka.dlq;

import dev.bum.admin_service.config.KafkaDlqProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@SuppressWarnings("unchecked")
class KafkaDlqReplayServiceTest {

    private final ConsumerFactory<byte[], byte[]> consumerFactory = mock(ConsumerFactory.class);
    private final Consumer<byte[], byte[]> consumer = mock(Consumer.class);
    private final KafkaTemplate<byte[], byte[]> kafkaTemplate = mock(KafkaTemplate.class);

    private KafkaDlqReplayService service;

    @BeforeEach
    void setUp() {
        KafkaDlqProperties properties = new KafkaDlqProperties();
        properties.setMappings(Map.of("user-event.DLT", "user-event"));
        service = new KafkaDlqReplayService(properties, consumerFactory, kafkaTemplate);
    }

    @Test
    @DisplayName("DLT 메시지를 원본 topic으로 재발행")
    void replay_dlt_message_to_target_topic() {
        byte[] key = "user01".getBytes();
        byte[] value = "{\"userId\":\"user01\"}".getBytes();
        given(consumerFactory.createConsumer()).willReturn(consumer);
        given(consumer.poll(any())).willReturn(records("user-event.DLT", 0, 10L, key, value));
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        DlqMessageHandleResponse response = service.replay(request());

        assertThat(response.result()).isEqualTo("REPLAYED");
        assertThat(response.targetTopic()).isEqualTo("user-event");
        assertThat(response.messageKey()).isEqualTo("user01");
        then(consumer).should().assign(List.of(new TopicPartition("user-event.DLT", 0)));
        then(consumer).should().seek(new TopicPartition("user-event.DLT", 0), 10L);

        var captor = forClass(ProducerRecord.class);
        then(kafkaTemplate).should().send(captor.capture());
        ProducerRecord<byte[], byte[]> producerRecord = captor.getValue();
        assertThat(producerRecord.topic()).isEqualTo("user-event");
        assertThat(producerRecord.partition()).isZero();
        assertThat(producerRecord.key()).isEqualTo(key);
        assertThat(producerRecord.value()).isEqualTo(value);
    }

    @Test
    @DisplayName("DLT 메시지 폐기는 원본 topic 재발행 없이 record 존재만 확인")
    void discard_dlt_message_without_replay() {
        given(consumerFactory.createConsumer()).willReturn(consumer);
        given(consumer.poll(any())).willReturn(records("user-event.DLT", 0, 10L, "user01".getBytes(), "{}".getBytes()));

        DlqMessageHandleResponse response = service.discard(request());

        assertThat(response.result()).isEqualTo("DISCARDED");
        assertThat(response.targetTopic()).isNull();
        then(kafkaTemplate).should(never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("허용되지 않은 DLT topic은 거부")
    void reject_not_allowed_dlt_topic() {
        DlqMessageHandleRequest request = new DlqMessageHandleRequest(
                "unknown.DLT",
                0,
                10L,
                "admin",
                "일시 장애 복구"
        );

        assertThatThrownBy(() -> service.replay(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("허용되지 않은 DLT topic입니다: unknown.DLT");
    }

    private DlqMessageHandleRequest request() {
        return new DlqMessageHandleRequest(
                "user-event.DLT",
                0,
                10L,
                "admin",
                "일시 장애 복구"
        );
    }

    private ConsumerRecords<byte[], byte[]> records(
            String topic,
            int partition,
            long offset,
            byte[] key,
            byte[] value
    ) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(topic, partition, offset, key, value);
        return new ConsumerRecords<>(Map.of(topicPartition, List.of(record)));
    }
}
