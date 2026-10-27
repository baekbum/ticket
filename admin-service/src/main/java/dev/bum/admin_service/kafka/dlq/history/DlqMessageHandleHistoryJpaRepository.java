package dev.bum.admin_service.kafka.dlq.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface DlqMessageHandleHistoryJpaRepository extends JpaRepository<DlqMessageHandleHistory, Long>, JpaSpecificationExecutor<DlqMessageHandleHistory> {

    Optional<DlqMessageHandleHistory> findTopByDltTopicAndPartitionNoAndMessageOffsetAndStatusOrderByHandledAtDesc(
            String dltTopic, int partitionNo, long messageOffset, DlqMessageHandleStatus status
    );

    Optional<DlqMessageHandleHistory> findTopByDltTopicAndPartitionNoAndMessageOffsetOrderByHandledAtDesc(
            String dltTopic,
            int partitionNo,
            long messageOffset
    );

}
