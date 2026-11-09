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

    // PG 계층이 가맹점 소유권을 확인한 UUID 형식 문자열로 원승인 거래를 찾는다.
    @EntityGraph(attributePaths = "dummyCard")
    Optional<DummyCardPaymentHistory> findByPgPaymentId(String pgPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from DummyCardPaymentHistory h where h.pgPaymentId = :id")
    Optional<DummyCardPaymentHistory> findByPgPaymentIdForUpdate(@Param("id") String pgPaymentId);

    @EntityGraph(attributePaths = "dummyCard")
    @Query("select h from DummyCardPaymentHistory h where h.paymentNo = :paymentNo and h.pgPaymentId is null")
    Optional<DummyCardPaymentHistory> findByPaymentNo(@Param("paymentNo") String paymentNo);

    @Query("select (count(h) > 0) from DummyCardPaymentHistory h where h.paymentNo = :paymentNo and h.pgPaymentId is null")
    boolean existsByPaymentNo(@Param("paymentNo") String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h from DummyCardPaymentHistory h where h.paymentNo = :paymentNo
              and h.transactionId = :transactionId and h.pgPaymentId is null
            """)
    Optional<DummyCardPaymentHistory> findByPaymentNoAndTransactionId(
            @Param("paymentNo") String paymentNo,
            @Param("transactionId") String transactionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from DummyCardPaymentHistory h where h.paymentNo = :paymentNo and h.pgPaymentId is null")
    Optional<DummyCardPaymentHistory> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    @Query("""
            select h.paymentNo from DummyCardPaymentHistory h
             where h.status in :statuses and h.pgPaymentId is null order by h.updatedAt, h.historyId
            """)
    List<String> findPendingPaymentNos(
            @Param("statuses") Collection<CardPaymentHistoryStatus> statuses,
            Pageable pageable
    );
}
