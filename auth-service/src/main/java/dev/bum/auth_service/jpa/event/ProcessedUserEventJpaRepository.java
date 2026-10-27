package dev.bum.auth_service.jpa.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProcessedUserEventJpaRepository extends JpaRepository<ProcessedUserEvent, Long> {
    // 같은 회원·멱등키의 최초 요청끼리 경합해도 UNIQUE 제약으로 직렬화한다. DB 반영 실패 시 이 기록도 롤백된다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into processed_user_events (idempotency_key, auth_id, event_version, user_id, event_type, request_hash, processed_at)
            values (:idempotencyKey, :authId, :eventVersion, :userId, :eventType, :requestHash, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("idempotencyKey") String idempotencyKey, @Param("authId") Long authId,
                       @Param("eventVersion") Long eventVersion,
                       @Param("userId") String userId, @Param("eventType") String eventType,
                       @Param("requestHash") String requestHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProcessedUserEvent e where e.authId = :authId and e.idempotencyKey = :idempotencyKey")
    Optional<ProcessedUserEvent> findByAuthIdAndIdempotencyKeyForUpdate(
            @Param("authId") Long authId, @Param("idempotencyKey") String idempotencyKey
    );
}
