package dev.bum.ticket_service.jpa.payment.virtualAccount;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VirtualAccountCancelOutboxJpaRepository extends JpaRepository<VirtualAccountCancelOutbox, Long> {

    Optional<VirtualAccountCancelOutbox> findByPaymentNo(String paymentNo);

    @Query("select o.outboxId from VirtualAccountCancelOutbox o where o.paymentNo = :paymentNo")
    Optional<Long> findIdByPaymentNo(@Param("paymentNo") String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from VirtualAccountCancelOutbox o where o.outboxId = :id")
    Optional<VirtualAccountCancelOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o.outboxId from VirtualAccountCancelOutbox o
            where o.status = :status and o.nextAttemptAt <= :now
              and (o.leaseUntil is null or o.leaseUntil <= :now)
            order by o.nextAttemptAt, o.outboxId
            """)
    List<Long> findReadyIds(
            @Param("status") VirtualAccountCancelOutboxStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
