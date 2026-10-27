package dev.bum.admin_service.kafka.dlq.message;

import dev.bum.admin_service.kafka.dlq.dto.response.DlqMessageDetailResponse;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqMessageSummaryResponse;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqTopicResponse;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleHistory;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleHistoryJpaRepository;
import dev.bum.admin_service.kafka.dlq.support.DlqRecordCodec;

import dev.bum.admin_service.config.KafkaDlqProperties;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KafkaDlqQueryService {
    private final KafkaDlqProperties properties;
    private final DlqMessageJpaRepository repository;
    private final DlqMessageHandleHistoryJpaRepository historyRepository;
    private final DlqRecordCodec codec;

    public List<DlqTopicResponse> topics() {
        return properties.getMappings().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new DlqTopicResponse(entry.getKey(), entry.getValue())).toList();
    }

    public List<DlqMessageSummaryResponse> messages(String dltTopic, Integer partition, Long fromOffset, Integer size) {
        return messages(dltTopic, partition, fromOffset, size, 0, null);
    }

    // Kafka 연결 없이 DB에서 페이지와 상태 조건을 적용한다. fromOffset은 지정 파티션의 시작 위치다.
    public List<DlqMessageSummaryResponse> messages(
            String dltTopic, Integer partition, Long fromOffset,
            Integer size, Integer page, DlqMessageStatus status) {

        resolveTargetTopic(dltTopic);

        if (fromOffset != null && partition == null) {
            throw new IllegalArgumentException("시작 오프셋을 지정할 때는 파티션도 지정해 주세요.");
        }

        Sort sort = fromOffset == null ? Sort.by(Sort.Direction.DESC, "recordTimestamp", "id")
                : Sort.by("messageOffset");

        return repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("dltTopic"), dltTopic));

            if (partition != null) predicates.add(cb.equal(root.get("partitionNo"), partition));
            if (fromOffset != null) predicates.add(cb.greaterThanOrEqualTo(root.get("messageOffset"), fromOffset));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));

            return cb.and(predicates.toArray(new Predicate[0]));
        }, PageRequest.of(page == null ? 0 : Math.max(0, page), size == null || size <= 0 ? 20 : Math.min(size, 100), sort))
                .stream().map(this::summaryOf).toList();
    }

    public DlqMessageDetailResponse detail(String dltTopic, int partition, long offset) {
        String targetTopic = resolveTargetTopic(dltTopic);

        DlqMessage message = repository.findByDltTopicAndPartitionNoAndMessageOffset(dltTopic, partition, offset)
                .orElseThrow(() -> new IllegalArgumentException("DB에 수집된 DLT 메시지를 찾을 수 없습니다."));

        Optional<DlqMessageHandleHistory> history = message.getCurrentHistoryId() == null
                ? historyRepository.findTopByDltTopicAndPartitionNoAndMessageOffsetOrderByHandledAtDesc(dltTopic, partition, offset)
                : historyRepository.findById(message.getCurrentHistoryId());

        return new DlqMessageDetailResponse(
                dltTopic, partition, offset, targetTopic,
                DlqRecordCodec.decode(message.getMessageKey()), DlqRecordCodec.decode(message.getPayload()),
                DlqRecordCodec.base64(message.getPayload()), codec.responses(message.getHeadersJson()), occurredAt(message),
                message.getStatus().name(),
                history.map(h -> h.getAction().name()).orElse(null),
                history.map(h -> h.getStatus().name()).orElse(null),
                history.map(DlqMessageHandleHistory::getOperator).orElse(null),
                history.map(DlqMessageHandleHistory::getReason).orElse(null),
                history.map(DlqMessageHandleHistory::getErrorMessage).orElse(null),
                history.map(DlqMessageHandleHistory::getHandledAt).orElse(null),
                history.map(DlqMessageHandleHistory::isPayloadModified).orElse(false),
                history.map(DlqMessageHandleHistory::getOriginalPayload).orElse(null),
                history.map(DlqMessageHandleHistory::getModifiedPayload).orElse(null)
        );
    }

    private DlqMessageSummaryResponse summaryOf(DlqMessage message) {
        String payload = DlqRecordCodec.decode(message.getPayload());
        String preview = payload == null || payload.length() <= 160 ? payload : payload.substring(0, 160) + "...";

        return new DlqMessageSummaryResponse(message.getDltTopic(), message.getPartitionNo(), message.getMessageOffset(),
                DlqRecordCodec.decode(message.getMessageKey()), preview, codec.responses(message.getHeadersJson()),
                occurredAt(message), message.getStatus().name());
    }

    private LocalDateTime occurredAt(DlqMessage message) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(message.getRecordTimestamp()), ZoneId.systemDefault());
    }

    private String resolveTargetTopic(String topic) {
        String target = properties.targetTopicOf(topic);

        if (!StringUtils.hasText(target)) throw new IllegalArgumentException("허용되지 않은 DLT topic입니다: " + topic);

        return target;
    }
}
