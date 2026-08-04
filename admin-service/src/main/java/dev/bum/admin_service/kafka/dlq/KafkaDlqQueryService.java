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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class KafkaDlqQueryService {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String PROCESSING_STATUS_UNKNOWN = "UNKNOWN";

    private final KafkaDlqProperties properties;
    private final ConsumerFactory<byte[], byte[]> kafkaDlqConsumerFactory;
    private final DlqMessageHandleHistoryJpaRepository historyRepository;

    /**
     * 관리자 화면에서 조회 가능한 DLT topic 목록을 반환한다.
     * Kafka broker를 직접 조회하지 않고, 운영자가 허용한 설정 매핑 기준으로만 노출한다.
     */
    public List<DlqTopicResponse> topics() {
        return properties.getMappings()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DlqTopicResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * 선택한 DLT topic의 메시지 목록을 조회한다.
     * 화면의 조회 버튼과 연결되며, partition/fromOffset/size 조건에 맞춰 Kafka record를 읽어 요약 정보로 변환한다.
     */
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

    /**
     * 특정 DLT 메시지의 상세 정보를 조회한다.
     * 목록에서 선택한 topic/partition/offset으로 원본 payload와 header를 다시 읽어 반환한다.
     */
    public DlqMessageDetailResponse detail(String dltTopic, int partition, long offset) {
        String targetTopic = resolveTargetTopic(dltTopic);
        ConsumerRecord<byte[], byte[]> record = readRecord(dltTopic, partition, offset);
        Optional<DlqMessageHandleHistory> handleHistory =
                historyRepository.findTopByDltTopicAndPartitionNoAndMessageOffsetOrderByHandledAtDesc(dltTopic, partition, offset);

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
                handleHistory.map(this::processingStatusOf).orElse(PROCESSING_STATUS_UNKNOWN),
                handleHistory.map(history -> history.getAction().name()).orElse(null),
                handleHistory.map(history -> history.getStatus().name()).orElse(null),
                handleHistory.map(DlqMessageHandleHistory::getOperator).orElse(null),
                handleHistory.map(DlqMessageHandleHistory::getReason).orElse(null),
                handleHistory.map(DlqMessageHandleHistory::getErrorMessage).orElse(null),
                handleHistory.map(DlqMessageHandleHistory::getHandledAt).orElse(null)
        );
    }

    /**
     * 요청한 DLT topic이 운영 설정에 등록된 허용 대상인지 검증하고 원본 topic을 반환한다.
     */
    private String resolveTargetTopic(String dltTopic) {
        String targetTopic = properties.targetTopicOf(dltTopic);
        if (!StringUtils.hasText(targetTopic)) {
            throw new IllegalArgumentException("허용되지 않은 DLT topic입니다: " + dltTopic);
        }
        return targetTopic;
    }

    /**
     * 조회 대상 partition 목록을 결정한다.
     * partition이 지정되면 해당 partition만 조회하고, 없으면 topic의 전체 partition을 조회한다.
     */
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

    /**
     * 지정된 consumer 위치에서 DLT 메시지를 읽는다.
     * 조회 시작 시점의 end offset까지만 읽어 조회 중 새로 유입된 메시지가 섞이지 않게 한다.
     */
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

    /**
     * 특정 topic/partition/offset의 단일 DLT 메시지를 읽는다.
     */
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

    /**
     * Kafka record를 목록 화면에 표시할 요약 응답으로 변환한다.
     */
    private DlqMessageSummaryResponse summaryOf(ConsumerRecord<byte[], byte[]> record) {
        return new DlqMessageSummaryResponse(
                record.topic(),
                record.partition(),
                record.offset(),
                decode(record.key()),
                preview(record.value()),
                headersOf(record),
                occurredAt(record),
                processingStatusOf(record.topic(), record.partition(), record.offset())
        );
    }

    private String processingStatusOf(String dltTopic, int partition, long offset) {
        return historyRepository.findTopByDltTopicAndPartitionNoAndMessageOffsetOrderByHandledAtDesc(dltTopic, partition, offset)
                .map(this::processingStatusOf)
                .orElse(PROCESSING_STATUS_UNKNOWN);
    }

    private String processingStatusOf(DlqMessageHandleHistory history) {
        if (history.getStatus() == DlqMessageHandleStatus.FAILED) {
            return "FAILED";
        }

        if (history.getAction() == DlqMessageHandleAction.REPLAY) {
            return "REPLAYED";
        }

        if (history.getAction() == DlqMessageHandleAction.DISCARD) {
            return "DISCARDED";
        }

        return PROCESSING_STATUS_UNKNOWN;
    }

    /**
     * Kafka record header를 화면 표시용 응답으로 변환한다.
     */
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

    /**
     * Kafka record timestamp를 서버 기본 시간대의 발생 시각으로 변환한다.
     */
    private LocalDateTime occurredAt(ConsumerRecord<byte[], byte[]> record) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp()), ZoneId.systemDefault());
    }

    /**
     * 조회 건수를 기본값과 최대값 범위 안으로 보정한다.
     */
    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * 목록 화면에서 payload가 과도하게 길어지지 않도록 미리보기 문자열로 축약한다.
     */
    private String preview(byte[] value) {
        String decoded = decode(value);
        if (decoded == null || decoded.length() <= 160) {
            return decoded;
        }
        return decoded.substring(0, 160) + "...";
    }

    /**
     * Kafka byte 값을 UTF-8 문자열로 변환한다.
     */
    private String decode(byte[] value) {
        if (value == null) {
            return null;
        }
        return new String(value, StandardCharsets.UTF_8);
    }

    /**
     * 원본 byte 값을 손실 없이 확인할 수 있도록 Base64 문자열로 변환한다.
     */
    private String base64(byte[] value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value);
    }
}
