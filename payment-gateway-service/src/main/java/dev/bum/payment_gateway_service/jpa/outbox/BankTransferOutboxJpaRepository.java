package dev.bum.payment_gateway_service.jpa.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BankTransferOutboxJpaRepository extends JpaRepository<BankTransferOutbox, Long> {

    Optional<BankTransferOutbox> findByFlowAndPgPaymentId(BankTransferOutbox.Flow flow, String pgPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from BankTransferOutbox e where e.outboxId = :id")
    Optional<BankTransferOutbox> findForUpdate(@Param("id") Long id);

    @Query("""
            select e.outboxId from BankTransferOutbox e where e.flow = :flow
              and ((e.status = 'PENDING' and e.nextAttemptAt <= :now)
                or (e.status = 'SENDING' and e.leaseUntil <= :now))
            order by e.outboxId
            """)
    List<Long> findReadyIds(@Param("flow") BankTransferOutbox.Flow flow, @Param("now") LocalDateTime now, Pageable page);
}
