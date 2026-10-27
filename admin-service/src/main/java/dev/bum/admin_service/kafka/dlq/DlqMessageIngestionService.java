package dev.bum.admin_service.kafka.dlq;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DlqMessageIngestionService {
    private final DlqMessageJpaRepository repository;
    private final DlqMessageHandleHistoryJpaRepository histories;
    private final DlqRecordCodec codec;

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
