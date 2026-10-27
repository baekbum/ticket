package dev.bum.admin_service.kafka.dlq;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface DlqMessageJpaRepository extends JpaRepository<DlqMessage, Long>, JpaSpecificationExecutor<DlqMessage> {
    Optional<DlqMessage> findByDltTopicAndPartitionNoAndMessageOffset(String topic, int partition, long offset);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from DlqMessage m where m.dltTopic = :topic and m.partitionNo = :partition and m.messageOffset = :offset")
    Optional<DlqMessage> findForUpdate(String topic, int partition, long offset);

    @Modifying
    @Query(value = """
            INSERT INTO dlq_messages
              (dlt_topic, partition_no, message_offset, message_key, payload, headers_json,
               record_timestamp, status, updated_at)
            VALUES (:topic, :partition, :offset, :key, :payload, :headers, :timestamp, :status, CURRENT_TIMESTAMP)
            ON CONFLICT (dlt_topic, partition_no, message_offset) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(String topic, int partition, long offset, byte[] key, byte[] payload,
                       String headers, long timestamp, String status);
}
