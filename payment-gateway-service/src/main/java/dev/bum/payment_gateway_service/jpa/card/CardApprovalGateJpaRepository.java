package dev.bum.payment_gateway_service.jpa.card;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CardApprovalGateJpaRepository extends JpaRepository<CardApprovalGate, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from CardApprovalGate g where g.pgPaymentId = :id")
    Optional<CardApprovalGate> findForUpdate(@Param("id") String id);
}
