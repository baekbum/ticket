package dev.bum.admin_service.kafka.dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final DlqMessageHandleHistoryJpaRepository historyRepository;
    private final ObjectMapper objectMapper;

    public DlqMessageHandleResponse replay(DlqMessageHandleRequest request) {
        String targetTopic = resolveTargetTopic(request.getDltTopic());
        assertNotAlreadyHandled(request);

        try {
            ConsumerRecord<byte[], byte[]> record = readRecord(request.getDltTopic(), request.getPartition(), request.getOffset());
            String messageKey = messageKeyOf(record);
            String originalPayload = payloadOf(record);

            kafkaTemplate.send(new ProducerRecord<>(targetTopic, record.partition(), record.key(), record.value())).join();

            saveHistory(request, DlqMessageHandleAction.REPLAY, DlqMessageHandleStatus.SUCCESS, targetTopic, messageKey, null, originalPayload, null, false);

            log.info("[DLQ-REPLAY] DLT 메시지 재발행 완료. dltTopic={}, partition={}, offset={}, targetTopic={}, operator={}, reason={}",
                    request.getDltTopic(), request.getPartition(), request.getOffset(), targetTopic, request.getOperator(), request.getReason());

            return response("REPLAYED", request, targetTopic, messageKey);
        } catch (RuntimeException e) {
            saveHistory(request, DlqMessageHandleAction.REPLAY, DlqMessageHandleStatus.FAILED, targetTopic, null, e.getMessage(), null, null, false);
            throw e;
        }
    }

    public DlqMessageHandleResponse replayModified(DlqMessageModifiedReplayRequest request) {
        String targetTopic = resolveTargetTopic(request.getDltTopic());
        DlqMessageHandleRequest handleRequest = handleRequestOf(request);
        assertNotAlreadyHandled(handleRequest);

        try {
            validateJsonPayload(request.getModifiedPayload());
            ConsumerRecord<byte[], byte[]> record = readRecord(request.getDltTopic(), request.getPartition(), request.getOffset());
            String messageKey = messageKeyOf(record);
            String originalPayload = payloadOf(record);
            byte[] modifiedValue = request.getModifiedPayload().getBytes(StandardCharsets.UTF_8);

            kafkaTemplate.send(new ProducerRecord<>(targetTopic, record.partition(), record.key(), modifiedValue)).join();

            saveHistory(
                    handleRequest,
                    DlqMessageHandleAction.MODIFIED_REPLAY,
                    DlqMessageHandleStatus.SUCCESS,
                    targetTopic,
                    messageKey,
                    null,
                    originalPayload,
                    request.getModifiedPayload(),
                    true
            );

            log.info("[DLQ-MODIFIED-REPLAY] DLT 메시지 payload 보정 후 재발행 완료. dltTopic={}, partition={}, offset={}, targetTopic={}, operator={}, reason={}",
                    request.getDltTopic(), request.getPartition(), request.getOffset(), targetTopic, request.getOperator(), request.getReason());

            return response("MODIFIED_REPLAYED", handleRequest, targetTopic, messageKey);
        } catch (RuntimeException e) {
            saveHistory(
                    handleRequest,
                    DlqMessageHandleAction.MODIFIED_REPLAY,
                    DlqMessageHandleStatus.FAILED,
                    targetTopic,
                    null,
                    e.getMessage(),
                    null,
                    request.getModifiedPayload(),
                    true
            );
            throw e;
        }
    }

    public DlqMessageHandleResponse discard(DlqMessageHandleRequest request) {
        resolveTargetTopic(request.getDltTopic());
        assertNotAlreadyHandled(request);

        try {
            ConsumerRecord<byte[], byte[]> record = readRecord(request.getDltTopic(), request.getPartition(), request.getOffset());
            String messageKey = messageKeyOf(record);
            String originalPayload = payloadOf(record);

            saveHistory(request, DlqMessageHandleAction.DISCARD, DlqMessageHandleStatus.SUCCESS, null, messageKey, null, originalPayload, null, false);

            log.info("[DLQ-DISCARD] DLT 메시지 폐기 처리. dltTopic={}, partition={}, offset={}, operator={}, reason={}",
                    request.getDltTopic(), request.getPartition(), request.getOffset(), request.getOperator(), request.getReason());

            return response("DISCARDED", request, null, messageKey);
        } catch (RuntimeException e) {
            saveHistory(request, DlqMessageHandleAction.DISCARD, DlqMessageHandleStatus.FAILED, null, null, e.getMessage(), null, null, false);
            throw e;
        }
    }

    private String resolveTargetTopic(String dltTopic) {
        String targetTopic = properties.targetTopicOf(dltTopic);
        if (!StringUtils.hasText(targetTopic)) {
            throw new IllegalArgumentException("허용되지 않은 DLT topic입니다: " + dltTopic);
        }
        return targetTopic;
    }

    private void assertNotAlreadyHandled(DlqMessageHandleRequest request) {
        boolean alreadyHandled = historyRepository.existsByDltTopicAndPartitionNoAndMessageOffsetAndStatus(
                request.getDltTopic(),
                request.getPartition(),
                request.getOffset(),
                DlqMessageHandleStatus.SUCCESS
        );
        if (alreadyHandled) {
            throw new IllegalStateException("이미 처리된 DLT 메시지입니다. dltTopic="
                    + request.getDltTopic()
                    + ", partition="
                    + request.getPartition()
                    + ", offset="
                    + request.getOffset());
        }
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
                request.getDltTopic(),
                request.getPartition(),
                request.getOffset(),
                targetTopic,
                messageKey,
                request.getOperator(),
                request.getReason()
        );
    }

    private void saveHistory(
            DlqMessageHandleRequest request,
            DlqMessageHandleAction action,
            DlqMessageHandleStatus status,
            String targetTopic,
            String messageKey,
            String errorMessage,
            String originalPayload,
            String modifiedPayload,
            boolean payloadModified
    ) {
        try {
            historyRepository.save(DlqMessageHandleHistory.builder()
                    .dltTopic(request.getDltTopic())
                    .partitionNo(request.getPartition())
                    .messageOffset(request.getOffset())
                    .messageKey(messageKey)
                    .targetTopic(targetTopic)
                    .action(action)
                    .status(status)
                    .operator(request.getOperator())
                    .reason(request.getReason())
                    .errorMessage(errorMessage)
                    .originalPayload(originalPayload)
                    .modifiedPayload(modifiedPayload)
                    .payloadModified(payloadModified)
                    .build());
        } catch (RuntimeException historyException) {
            log.warn("[DLQ-HISTORY] DLT 처리 이력 저장 실패. dltTopic={}, partition={}, offset={}, action={}, status={}",
                    request.getDltTopic(), request.getPartition(), request.getOffset(), action, status, historyException);
        }
    }

    private void validateJsonPayload(String payload) {
        try {
            objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("수정 payload는 유효한 JSON 형식이어야 합니다.", e);
        }
    }

    private DlqMessageHandleRequest handleRequestOf(DlqMessageModifiedReplayRequest request) {
        return new DlqMessageHandleRequest(
                request.getDltTopic(),
                request.getPartition(),
                request.getOffset(),
                request.getOperator(),
                request.getReason()
        );
    }

    private String messageKeyOf(ConsumerRecord<byte[], byte[]> record) {
        if (record.key() == null || record.key().length == 0) {
            return null;
        }

        return new String(record.key(), StandardCharsets.UTF_8);
    }

    private String payloadOf(ConsumerRecord<byte[], byte[]> record) {
        if (record.value() == null) {
            return null;
        }

        return new String(record.value(), StandardCharsets.UTF_8);
    }
}
