package dev.bum.payment_gateway_service.jpa.gateway;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GatewayCardNotificationJpaRepository extends JpaRepository<GatewayCardNotification, Long> {

    Optional<GatewayCardNotification> findByPaymentPgPaymentId(String pgPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from GatewayCardNotification n where n.notificationId = :id")
    Optional<GatewayCardNotification> findForUpdate(@Param("id") Long id);

    @Query("""
            select n.notificationId from GatewayCardNotification n
             where (n.status = 'PENDING' and n.nextAttemptAt <= :now)
                or (n.status = 'SENDING' and n.leaseUntil <= :now)
             order by n.notificationId
            """)
    List<Long> findReadyIds(@Param("now") LocalDateTime now, Pageable pageable);
}
