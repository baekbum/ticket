package dev.bum.admin_service.kafka.dlq;

import dev.bum.admin_service.config.KafkaDlqProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@SuppressWarnings("unchecked")
class KafkaDlqQueryServiceTest {

    private final ConsumerFactory<byte[], byte[]> consumerFactory = mock(ConsumerFactory.class);
    private final Consumer<byte[], byte[]> consumer = mock(Consumer.class);

    private KafkaDlqQueryService service;

    @BeforeEach
    void setUp() {
        KafkaDlqProperties properties = new KafkaDlqProperties();
        properties.setMappings(Map.of(
                "user-event.DLT", "user-event",
                "audit-log.DLT", "audit-log"
        ));
        service = new KafkaDlqQueryService(properties, consumerFactory);
    }

    @Test
    @DisplayName("허용된 DLT topic 목록을 반환")
    void topics_returns_allowed_mappings() {
        List<DlqTopicResponse> topics = service.topics();

        assertThat(topics).extracting(DlqTopicResponse::getDltTopic)
                .containsExactly("audit-log.DLT", "user-event.DLT");
        assertThat(topics).extracting(DlqTopicResponse::getTargetTopic)
                .containsExactly("audit-log", "user-event");
    }

    @Test
    @DisplayName("DLT 메시지 목록은 key, payload preview, header, 발생 시각을 반환")
    void messages_returns_record_summaries() {
        TopicPartition topicPartition = new TopicPartition("user-event.DLT", 0);
        ConsumerRecord<byte[], byte[]> record = record("user-event.DLT", 0, 10L);
        given(consumerFactory.createConsumer()).willReturn(consumer);
        given(consumer.partitionsFor("user-event.DLT")).willReturn(List.of(new PartitionInfo("user-event.DLT", 0, null, null, null)));
        given(consumer.endOffsets(List.of(topicPartition))).willReturn(Map.of(topicPartition, 11L));
        given(consumer.beginningOffsets(List.of(topicPartition))).willReturn(Map.of(topicPartition, 0L));
        given(consumer.poll(any())).willReturn(
                new ConsumerRecords<>(Map.of(topicPartition, List.of(record))),
                ConsumerRecords.empty(),
                ConsumerRecords.empty()
        );

        List<DlqMessageSummaryResponse> messages = service.messages("user-event.DLT", null, null, 20);

        assertThat(messages).hasSize(1);
        DlqMessageSummaryResponse message = messages.getFirst();
        assertThat(message.getDltTopic()).isEqualTo("user-event.DLT");
        assertThat(message.getPartition()).isZero();
        assertThat(message.getOffset()).isEqualTo(10L);
        assertThat(message.getMessageKey()).isEqualTo("user01");
        assertThat(message.getPayloadPreview()).isEqualTo("{\"userId\":\"user01\"}");
        assertThat(message.getHeaders()).extracting(DlqHeaderResponse::getKey).contains("kafka_dlt-exception-message");
        assertThat(message.getProcessingStatus()).isEqualTo("UNKNOWN");
        then(consumer).should().seek(topicPartition, 0L);
    }

    @Test
    @DisplayName("DLT 메시지 상세는 payload와 header 전체를 반환")
    void detail_returns_record_payload_and_headers() {
        TopicPartition topicPartition = new TopicPartition("user-event.DLT", 0);
        ConsumerRecord<byte[], byte[]> record = record("user-event.DLT", 0, 10L);
        given(consumerFactory.createConsumer()).willReturn(consumer);
        given(consumer.poll(any())).willReturn(new ConsumerRecords<>(Map.of(topicPartition, List.of(record))));

        DlqMessageDetailResponse detail = service.detail("user-event.DLT", 0, 10L);

        assertThat(detail.getTargetTopic()).isEqualTo("user-event");
        assertThat(detail.getMessageKey()).isEqualTo("user01");
        assertThat(detail.getPayload()).isEqualTo("{\"userId\":\"user01\"}");
        assertThat(detail.getPayloadBase64()).isEqualTo("eyJ1c2VySWQiOiJ1c2VyMDEifQ==");
        assertThat(detail.getHeaders()).hasSize(1);
        then(consumer).should().assign(List.of(topicPartition));
        then(consumer).should().seek(topicPartition, 10L);
    }

    private ConsumerRecord<byte[], byte[]> record(String topic, int partition, long offset) {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(
                topic,
                partition,
                offset,
                "user01".getBytes(StandardCharsets.UTF_8),
                "{\"userId\":\"user01\"}".getBytes(StandardCharsets.UTF_8)
        );
        record.headers().add(new RecordHeader(
                "kafka_dlt-exception-message",
                "db error".getBytes(StandardCharsets.UTF_8)
        ));
        return record;
    }
}
