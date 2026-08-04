package dev.bum.admin_service.kafka.dlq;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DlqMessageHandleHistoryJpaRepository extends JpaRepository<DlqMessageHandleHistory, Long> {

    Optional<DlqMessageHandleHistory> findTopByDltTopicAndPartitionNoAndMessageOffsetOrderByHandledAtDesc(
            String dltTopic,
            int partitionNo,
            long messageOffset
    );

    boolean existsByDltTopicAndPartitionNoAndMessageOffsetAndStatus(
            String dltTopic,
            int partitionNo,
            long messageOffset,
            DlqMessageHandleStatus status
    );
}
