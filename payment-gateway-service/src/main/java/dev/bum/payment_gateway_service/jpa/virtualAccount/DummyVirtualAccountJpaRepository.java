package dev.bum.payment_gateway_service.jpa.virtualAccount;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DummyVirtualAccountJpaRepository extends JpaRepository<DummyVirtualAccount, Long> {

    // PG 계층이 가맹점 소유권을 확인한 UUID 형식 문자열로 계좌를 찾는다.
    Optional<DummyVirtualAccount> findByPgPaymentId(String pgPaymentId);

    // 계좌를 잠그지 않고 PG 식별자만 찾는다. 후속 입금 흐름에서 PG 거래를 먼저 잠그기 위한 조회다.
    @Query("select a.pgPaymentId from DummyVirtualAccount a where a.accountNumber = :accountNumber")
    Optional<String> findPgPaymentIdByAccountNumber(@Param("accountNumber") String accountNumber);

    Optional<DummyVirtualAccount> findByPaymentNo(String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DummyVirtualAccount a where a.paymentNo = :paymentNo")
    Optional<DummyVirtualAccount> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DummyVirtualAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<DummyVirtualAccount> findTop100ByStatusOrderByDepositedAtAsc(VirtualAccountPaymentStatus status);

    // 입금과 만료가 같은 계좌 행을 잠가 상태 변경을 순서대로 처리한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DummyVirtualAccount> findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            VirtualAccountPaymentStatus status,
            LocalDateTime expiresAt
    );
}
