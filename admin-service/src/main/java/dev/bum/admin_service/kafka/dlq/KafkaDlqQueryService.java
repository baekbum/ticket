package dev.bum.admin_service.kafka.dlq;

import dev.bum.admin_service.config.KafkaDlqProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KafkaDlqQueryService {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String PROCESSING_STATUS_UNKNOWN = "UNKNOWN";

    private final KafkaDlqProperties properties;
    private final ConsumerFactory<byte[], byte[]> kafkaDlqConsumerFactory;

    public List<DlqTopicResponse> topics() {
        return properties.getMappings()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DlqTopicResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<DlqMessageSummaryResponse> messages(
            String dltTopic,
            Integer partition,
            Long fromOffset,
            Integer size
    ) {
        resolveTargetTopic(dltTopic);
        int normalizedSize = normalizeSize(size);

        try (Consumer<byte[], byte[]> consumer = kafkaDlqConsumerFactory.createConsumer()) {
            List<TopicPartition> topicPartitions = resolveTopicPartitions(consumer, dltTopic, partition);
            consumer.assign(topicPartitions);

            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);
            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(topicPartitions);

            for (TopicPartition topicPartition : topicPartitions) {
                long beginningOffset = beginningOffsets.getOrDefault(topicPartition, 0L);
                long endOffset = endOffsets.getOrDefault(topicPartition, 0L);
                long seekOffset = fromOffset != null
                        ? Math.max(beginningOffset, fromOffset)
                        : Math.max(beginningOffset, endOffset - normalizedSize);
                consumer.seek(topicPartition, seekOffset);
            }

            List<ConsumerRecord<byte[], byte[]>> records = pollRecords(consumer, endOffsets, normalizedSize);

            return records.stream()
                    .sorted(Comparator
                            .comparing(ConsumerRecord<byte[], byte[]>::timestamp).reversed()
                            .thenComparing(ConsumerRecord::partition)
                            .thenComparing(ConsumerRecord::offset))
                    .limit(normalizedSize)
                    .map(this::summaryOf)
                    .toList();
        }
    }

    public DlqMessageDetailResponse detail(String dltTopic, int partition, long offset) {
        String targetTopic = resolveTargetTopic(dltTopic);
        ConsumerRecord<byte[], byte[]> record = readRecord(dltTopic, partition, offset);

        return new DlqMessageDetailResponse(
                dltTopic,
                partition,
                offset,
                targetTopic,
                decode(record.key()),
                decode(record.value()),
                base64(record.value()),
                headersOf(record),
                occurredAt(record),
                PROCESSING_STATUS_UNKNOWN
        );
    }

    private String resolveTargetTopic(String dltTopic) {
        String targetTopic = properties.targetTopicOf(dltTopic);
        if (!StringUtils.hasText(targetTopic)) {
            throw new IllegalArgumentException("허용되지 않은 DLT topic입니다: " + dltTopic);
        }
        return targetTopic;
    }

    private List<TopicPartition> resolveTopicPartitions(
            Consumer<byte[], byte[]> consumer,
            String topic,
            Integer partition
    ) {
        if (partition != null) {
            return List.of(new TopicPartition(topic, partition));
        }

        List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
        if (partitionInfos == null || partitionInfos.isEmpty()) {
            throw new IllegalArgumentException("DLT topic partition 정보를 찾을 수 없습니다: " + topic);
        }

        return partitionInfos.stream()
                .map(partitionInfo -> new TopicPartition(topic, partitionInfo.partition()))
                .toList();
    }

    private List<ConsumerRecord<byte[], byte[]>> pollRecords(
            Consumer<byte[], byte[]> consumer,
            Map<TopicPartition, Long> endOffsets,
            int size
    ) {
        List<ConsumerRecord<byte[], byte[]>> records = new ArrayList<>();
        int emptyPollCount = 0;

        while (records.size() < size && emptyPollCount < 2) {
            ConsumerRecords<byte[], byte[]> polledRecords = consumer.poll(POLL_TIMEOUT);
            if (polledRecords.isEmpty()) {
                emptyPollCount++;
                continue;
            }

            emptyPollCount = 0;
            for (ConsumerRecord<byte[], byte[]> record : polledRecords) {
                long endOffset = endOffsets.getOrDefault(new TopicPartition(record.topic(), record.partition()), Long.MAX_VALUE);
                if (record.offset() < endOffset) {
                    records.add(record);
                }
                if (records.size() >= size) {
                    break;
                }
            }
        }

        return records;
    }

    private ConsumerRecord<byte[], byte[]> readRecord(String topic, int partition, long offset) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);

        try (Consumer<byte[], byte[]> consumer = kafkaDlqConsumerFactory.createConsumer()) {
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, offset);

            ConsumerRecords<byte[], byte[]> records = consumer.poll(POLL_TIMEOUT);
            return records.records(topicPartition)
                    .stream()
                    .filter(record -> record.offset() == offset)
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException("DLT 메시지를 찾을 수 없습니다. topic=" + topic + ", partition=" + partition + ", offset=" + offset)
                    );
        }
    }

    private DlqMessageSummaryResponse summaryOf(ConsumerRecord<byte[], byte[]> record) {
        return new DlqMessageSummaryResponse(
                record.topic(),
                record.partition(),
                record.offset(),
                decode(record.key()),
                preview(record.value()),
                headersOf(record),
                occurredAt(record),
                PROCESSING_STATUS_UNKNOWN
        );
    }

    private List<DlqHeaderResponse> headersOf(ConsumerRecord<byte[], byte[]> record) {
        List<DlqHeaderResponse> headers = new ArrayList<>();
        for (Header header : record.headers()) {
            headers.add(new DlqHeaderResponse(
                    header.key(),
                    decode(header.value()),
                    base64(header.value())
            ));
        }
        return headers;
    }

    private LocalDateTime occurredAt(ConsumerRecord<byte[], byte[]> record) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp()), ZoneId.systemDefault());
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String preview(byte[] value) {
        String decoded = decode(value);
        if (decoded == null || decoded.length() <= 160) {
            return decoded;
        }
        return decoded.substring(0, 160) + "...";
    }

    private String decode(byte[] value) {
        if (value == null) {
            return null;
        }
        return new String(value, StandardCharsets.UTF_8);
    }

    private String base64(byte[] value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value);
    }
}
