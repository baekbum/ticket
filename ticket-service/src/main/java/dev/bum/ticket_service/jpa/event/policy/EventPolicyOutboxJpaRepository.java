package dev.bum.ticket_service.jpa.event.policy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventPolicyOutboxJpaRepository extends JpaRepository<EventPolicyOutbox, Long> {
    boolean existsByEventId(Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from EventPolicyOutbox o where o.outboxId = :id")
    Optional<EventPolicyOutbox> findForUpdate(@Param("id") Long id);

    @Query("select o.outboxId from EventPolicyOutbox o where o.published = false and o.nextAttemptAt <= :now order by o.outboxId")
    List<Long> findReady(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("select o from EventPolicyOutbox o where o.eventId > :after and o.outboxId = (select max(n.outboxId) from EventPolicyOutbox n where n.eventId = o.eventId) order by o.eventId")
    List<EventPolicyOutbox> findLatestAfter(@Param("after") long after, Pageable pageable);
}
