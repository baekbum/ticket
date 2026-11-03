package dev.bum.admin_service.kafka.dlq.ingestion;

import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleHistoryJpaRepository;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleStatus;
import dev.bum.admin_service.kafka.dlq.message.DlqMessageJpaRepository;
import dev.bum.admin_service.kafka.dlq.support.DlqRecordCodec;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DlqMessageIngestionService {

    // 실패 메시지 데이터를 조회하고 저장하는 레포지토리.
    private final DlqMessageJpaRepository repository;
    // 처리 이력 목록.
    private final DlqMessageHandleHistoryJpaRepository histories;
    // 실패 메시지 본문과 헤더를 직렬화하고 복원하는 도구.
    private final DlqRecordCodec codec;

    /**
     * 실패 메시지를 저장한다.
     */
    @Transactional
    public void save(ConsumerRecord<byte[], byte[]> record) {

        // 전환 전에 이미 성공 처리한 메시지도 다시 처리 가능 상태로 노출하지 않는다.
        String status = histories.findTopByDltTopicAndPartitionNoAndMessageOffsetAndStatusOrderByHandledAtDesc(
                record.topic(), record.partition(), record.offset(), DlqMessageHandleStatus.SUCCESS
        )
                .map(h -> switch (h.getAction()) {
                    case REPLAY -> "REPLAYED";
                    case MODIFIED_REPLAY -> "MODIFIED_REPLAYED";
                    case DISCARD -> "DISCARDED";
                }).orElse("PENDING");

        repository.insertIfAbsent(record.topic(), record.partition(), record.offset(), record.key(),
                record.value(), codec.encode(record.headers()), record.timestamp(), status);
    }
}
