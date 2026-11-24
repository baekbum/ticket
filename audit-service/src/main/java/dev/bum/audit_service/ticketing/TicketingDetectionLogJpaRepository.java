package dev.bum.audit_service.ticketing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface TicketingDetectionLogJpaRepository extends JpaRepository<TicketingDetectionLogEntity, Long> {

    @Modifying
    @Query(value = """
            insert into ticketing_detection_log (
                event_id, user_id, session_key_hash, detection_type, request_id, evidence, occurred_at
            ) values (
                :eventId, :userId, :sessionKeyHash, :detectionType, :requestId, cast(:evidence as jsonb), :occurredAt
            ) on conflict (event_id, user_id, session_key_hash, detection_type) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("eventId") Long eventId,
            @Param("userId") String userId,
            @Param("sessionKeyHash") String sessionKeyHash,
            @Param("detectionType") String detectionType,
            @Param("requestId") String requestId,
            @Param("evidence") String evidence,
            @Param("occurredAt") OffsetDateTime occurredAt
    );
}
