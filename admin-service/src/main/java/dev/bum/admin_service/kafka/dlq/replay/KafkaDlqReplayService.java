package dev.bum.admin_service.kafka.dlq.replay;

import dev.bum.admin_service.kafka.dlq.dto.request.DlqMessageHandleRequest;
import dev.bum.admin_service.kafka.dlq.dto.request.DlqMessageModifiedReplayRequest;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqMessageHandleResponse;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleAction;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleStatus;
import dev.bum.admin_service.kafka.dlq.support.DlqRecordCodec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.config.KafkaDlqProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class KafkaDlqReplayService {
    private final KafkaDlqProperties properties;
    private final KafkaTemplate<byte[], byte[]> kafkaTemplate;
    private final DlqReplayTransactionService transactions;
    private final DlqRecordCodec codec;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka-dlq.send-timeout-ms:10000}")
    private long sendTimeoutMs = 10000;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DlqMessageHandleResponse replay(DlqMessageHandleRequest request) {
        return publish(request, DlqMessageHandleAction.REPLAY, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DlqMessageHandleResponse replayModified(DlqMessageModifiedReplayRequest request) {
        try {
            if (!StringUtils.hasText(request.getModifiedPayload()) || objectMapper.readTree(request.getModifiedPayload()) == null) {
                throw new IllegalArgumentException("수정 payload는 유효한 JSON 형식이어야 합니다.");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("수정 payload는 유효한 JSON 형식이어야 합니다.", e);
        }
        return publish(new DlqMessageHandleRequest(request.getDltTopic(), request.getPartition(), request.getOffset(),
                request.getOperator(), request.getReason()), DlqMessageHandleAction.MODIFIED_REPLAY, request.getModifiedPayload());
    }

    public DlqMessageHandleResponse discard(DlqMessageHandleRequest request) {
        resolveTargetTopic(request.getDltTopic());
        DlqReplayAttempt attempt = transactions.begin(request, null, DlqMessageHandleAction.DISCARD, null);
        return response("DISCARDED", request, attempt);
    }

    public void releaseStalled(DlqMessageHandleRequest request) {
        resolveTargetTopic(request.getDltTopic());
        transactions.releaseStalled(request);
    }

    private DlqMessageHandleResponse publish(DlqMessageHandleRequest request, DlqMessageHandleAction action, String modifiedPayload) {
        String target = resolveTargetTopic(request.getDltTopic());
        // A: 발행 전에 처리권과 시도 이력을 먼저 커밋한다.
        DlqReplayAttempt attempt = transactions.begin(request, target, action, modifiedPayload);
        try {
            ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(target, request.getPartition(), attempt.key(), attempt.payload());
            codec.headers(attempt.headersJson()).stream()
                    // DLT 진단 헤더는 DB에 보관하고 원본 업무 헤더만 다시 전달한다.
                    .filter(h -> !h.key().startsWith("kafka_dlt-") && !h.key().startsWith("kafka_exception-"))
                    .forEach(h -> record.headers().add(h.key(), h.value()));
            kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            // 타임아웃/통신 단절은 브로커에 저장되었을 수도 있으므로 확정 실패와 구분한다.
            DlqMessageHandleStatus status = cause instanceof SerializationException
                    || cause instanceof RecordTooLargeException || cause instanceof AuthorizationException
                    ? DlqMessageHandleStatus.FAILED : DlqMessageHandleStatus.UNKNOWN;
            try {
                transactions.finish(request, attempt.historyId(), status, cause.getClass().getSimpleName());
            } catch (RuntimeException recordingFailure) {
                e.addSuppressed(recordingFailure);
            }
            throw new IllegalStateException("재발행 결과를 확인해 주세요. 상태=" + status, e);
        }
        // B: 발행 성공 후 상태 저장 실패를 Kafka 실패로 덮어쓰지 않는다. REPLAYING 이력으로 복구한다.
        transactions.finish(request, attempt.historyId(), DlqMessageHandleStatus.SUCCESS, null);
        return response(action == DlqMessageHandleAction.REPLAY ? "REPLAYED" : "MODIFIED_REPLAYED", request, attempt);
    }

    private String resolveTargetTopic(String topic) {
        String target = properties.targetTopicOf(topic);
        if (!StringUtils.hasText(target)) throw new IllegalArgumentException("허용되지 않은 DLT topic입니다: " + topic);
        return target;
    }

    private DlqMessageHandleResponse response(String result, DlqMessageHandleRequest request, DlqReplayAttempt attempt) {
        return new DlqMessageHandleResponse(result, request.getDltTopic(), request.getPartition(), request.getOffset(),
                attempt.targetTopic(), DlqRecordCodec.decode(attempt.key()), request.getOperator(), request.getReason());
    }
}
