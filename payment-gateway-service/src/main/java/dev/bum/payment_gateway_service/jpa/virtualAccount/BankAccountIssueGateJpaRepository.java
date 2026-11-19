package dev.bum.payment_gateway_service.jpa.virtualAccount;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface BankAccountIssueGateJpaRepository extends JpaRepository<BankAccountIssueGate, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from BankAccountIssueGate g where g.pgPaymentId = :id")
    Optional<BankAccountIssueGate> findForUpdate(@Param("id") String id);
}
