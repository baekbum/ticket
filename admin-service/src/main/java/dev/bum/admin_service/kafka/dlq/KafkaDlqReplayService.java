package dev.bum.admin_service.kafka.dlq;

import dev.bum.admin_service.config.KafkaDlqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaDlqReplayService {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);

    private final KafkaDlqProperties properties;
    private final ConsumerFactory<byte[], byte[]> kafkaDlqConsumerFactory;
    private final KafkaTemplate<byte[], byte[]> kafkaTemplate;

    public DlqMessageHandleResponse replay(DlqMessageHandleRequest request) {
        String targetTopic = resolveTargetTopic(request.dltTopic());
        ConsumerRecord<byte[], byte[]> record = readRecord(request.dltTopic(), request.partition(), request.offset());

        kafkaTemplate.send(new ProducerRecord<>(targetTopic, record.partition(), record.key(), record.value())).join();

        log.info("[DLQ-REPLAY] DLT 메시지 재발행 완료. dltTopic={}, partition={}, offset={}, targetTopic={}, operator={}, reason={}",
                request.dltTopic(), request.partition(), request.offset(), targetTopic, request.operator(), request.reason());

        return response("REPLAYED", request, targetTopic, messageKeyOf(record));
    }

    public DlqMessageHandleResponse discard(DlqMessageHandleRequest request) {
        resolveTargetTopic(request.dltTopic());
        ConsumerRecord<byte[], byte[]> record = readRecord(request.dltTopic(), request.partition(), request.offset());

        log.info("[DLQ-DISCARD] DLT 메시지 폐기 처리. dltTopic={}, partition={}, offset={}, operator={}, reason={}",
                request.dltTopic(), request.partition(), request.offset(), request.operator(), request.reason());

        return response("DISCARDED", request, null, messageKeyOf(record));
    }

    private String resolveTargetTopic(String dltTopic) {
        String targetTopic = properties.targetTopicOf(dltTopic);
        if (!StringUtils.hasText(targetTopic)) {
            throw new IllegalArgumentException("허용되지 않은 DLT topic입니다: " + dltTopic);
        }
        return targetTopic;
    }

    private ConsumerRecord<byte[], byte[]> readRecord(String topic, int partition, long offset) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);

        try (Consumer<byte[], byte[]> consumer = kafkaDlqConsumerFactory.createConsumer()) {
            consumer.assign(java.util.List.of(topicPartition));
            consumer.seek(topicPartition, offset);

            ConsumerRecords<byte[], byte[]> records = consumer.poll(POLL_TIMEOUT);
            Optional<ConsumerRecord<byte[], byte[]>> matchedRecord = records.records(topicPartition)
                    .stream()
                    .filter(record -> record.offset() == offset)
                    .findFirst();

            return matchedRecord.orElseThrow(() ->
                    new IllegalArgumentException("DLT 메시지를 찾을 수 없습니다. topic=" + topic + ", partition=" + partition + ", offset=" + offset)
            );
        }
    }

    private DlqMessageHandleResponse response(
            String result,
            DlqMessageHandleRequest request,
            String targetTopic,
            String messageKey
    ) {
        return new DlqMessageHandleResponse(
                result,
                request.dltTopic(),
                request.partition(),
                request.offset(),
                targetTopic,
                messageKey,
                request.operator(),
                request.reason()
        );
    }

    private String messageKeyOf(ConsumerRecord<byte[], byte[]> record) {
        if (record.key() == null || record.key().length == 0) {
            return null;
        }

        return new String(record.key(), StandardCharsets.UTF_8);
    }
}
