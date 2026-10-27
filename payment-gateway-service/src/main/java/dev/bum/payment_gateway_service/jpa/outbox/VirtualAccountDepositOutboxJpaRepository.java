package dev.bum.payment_gateway_service.jpa.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VirtualAccountDepositOutboxJpaRepository extends JpaRepository<VirtualAccountDepositOutbox, Long> {
    Optional<VirtualAccountDepositOutbox> findByPaymentNo(String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from VirtualAccountDepositOutbox o where o.outboxId = :id")
    Optional<VirtualAccountDepositOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o.outboxId from VirtualAccountDepositOutbox o
            where o.status = :status and o.nextAttemptAt <= :now
            order by o.nextAttemptAt, o.outboxId
            """)
    List<Long> findReadyIds(@Param("status") VirtualAccountDepositOutboxStatus status,
                           @Param("now") LocalDateTime now, Pageable pageable);
}
