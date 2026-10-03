package dev.bum.ticket_service.jpa.ticket;

import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface TicketPurchaseLockJpaRepository extends JpaRepository<TicketPurchaseLock, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO ticket_purchase_locks (user_id, limit_scope, scope_key, ticket_count, created_at)
            VALUES (:userId, :limitScope, :scopeKey, :ticketCount, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId,
                       @Param("limitScope") String limitScope,
                       @Param("scopeKey") String scopeKey,
                       @Param("ticketCount") long ticketCount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from TicketPurchaseLock l
            where l.userId = :userId and l.limitScope = :limitScope and l.scopeKey = :scopeKey
            """)
    Optional<TicketPurchaseLock> findForUpdate(@Param("userId") String userId,
                                              @Param("limitScope") TicketLimitScope limitScope,
                                              @Param("scopeKey") String scopeKey);

    /** 최초 요청도 고유 제약으로 행을 하나만 만들고, 예매 트랜잭션 종료까지 잠근다. */
    @Transactional(propagation = Propagation.MANDATORY)
    default void acquire(String userId, TicketLimitScope limitScope, String scopeKey) {
        insertIfAbsent(userId, limitScope.name(), scopeKey, 0);
        findForUpdate(userId, limitScope, scopeKey)
                .orElseThrow(() -> new IllegalStateException("구매 매수 검증 잠금 정보를 찾을 수 없습니다."));
    }
}
