package dev.bum.user_service.jpa.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserOutboxEventJpaRepository extends JpaRepository<UserOutboxEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UserOutboxEvent e where e.outboxId = :id")
    Optional<UserOutboxEvent> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUserIdAndStatusAndOutboxIdLessThan(String userId, UserOutboxStatus status, Long id);

    // 이전 이벤트가 남은 회원은 순서를 지키고, 다른 회원의 발행은 계속 진행한다.
    @Query("""
            select e.outboxId from UserOutboxEvent e
            where e.status = :status and e.nextAttemptAt <= :now
              and not exists (
                select previous.outboxId from UserOutboxEvent previous
                where previous.userId = e.userId and previous.status = :status
                  and previous.outboxId < e.outboxId
              )
            order by e.outboxId
            """)
    List<Long> findReadyIds(@Param("status") UserOutboxStatus status, @Param("now") LocalDateTime now, Pageable pageable);
}
