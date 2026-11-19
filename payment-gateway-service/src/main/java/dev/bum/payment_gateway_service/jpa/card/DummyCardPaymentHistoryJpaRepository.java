package dev.bum.payment_gateway_service.jpa.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface DummyCardPaymentHistoryJpaRepository extends JpaRepository<DummyCardPaymentHistory, Long> {

    // PG 계층이 가맹점 소유권을 확인한 UUID 형식 문자열로 원승인 거래를 찾는다.
    @EntityGraph(attributePaths = "dummyCard")
    Optional<DummyCardPaymentHistory> findByPgPaymentId(String pgPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from DummyCardPaymentHistory h where h.pgPaymentId = :id")
    Optional<DummyCardPaymentHistory> findByPgPaymentIdForUpdate(@Param("id") String pgPaymentId);

}
