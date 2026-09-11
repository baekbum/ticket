package dev.bum.payment_gateway_service.jpa.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Collection;

import java.util.Optional;

public interface DummyCardPaymentHistoryJpaRepository extends JpaRepository<DummyCardPaymentHistory, Long> {

    @EntityGraph(attributePaths = "dummyCard")
    Optional<DummyCardPaymentHistory> findByPaymentNo(String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DummyCardPaymentHistory> findByPaymentNoAndTransactionId(String paymentNo, String transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from DummyCardPaymentHistory h where h.paymentNo = :paymentNo")
    Optional<DummyCardPaymentHistory> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    @Query("select h.paymentNo from DummyCardPaymentHistory h where h.status in :statuses order by h.updatedAt, h.historyId")
    List<String> findPendingPaymentNos(@Param("statuses") Collection<CardPaymentHistoryStatus> statuses, Pageable pageable);
}
