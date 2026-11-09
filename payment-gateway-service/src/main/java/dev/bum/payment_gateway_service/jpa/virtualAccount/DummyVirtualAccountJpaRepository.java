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

    // 신규 금융 입금은 PG 연결 계좌만 잠근다. 기존 paymentNo 전용 경로와 섞지 않는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DummyVirtualAccount a where a.accountNumber = :number and a.pgPaymentId is not null")
    Optional<DummyVirtualAccount> findLinkedByAccountNumberForUpdate(@Param("number") String number);

    // 취소 예매 응답을 정리할 때도 같은 은행 계좌 잠금을 사용한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DummyVirtualAccount a where a.pgPaymentId = :id")
    Optional<DummyVirtualAccount> findLinkedByPgPaymentIdForUpdate(@Param("id") String id);

    // 기존 Ticksy 전용 조회에서는 가맹점 연결 거래를 찾지 않는다.
    @Query("select a from DummyVirtualAccount a where a.paymentNo = :paymentNo and a.pgPaymentId is null")
    Optional<DummyVirtualAccount> findByPaymentNo(@Param("paymentNo") String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DummyVirtualAccount a where a.paymentNo = :paymentNo and a.pgPaymentId is null")
    Optional<DummyVirtualAccount> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DummyVirtualAccount a where a.accountNumber = :accountNumber and a.pgPaymentId is null")
    Optional<DummyVirtualAccount> findByAccountNumber(@Param("accountNumber") String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<DummyVirtualAccount> findTop100ByStatusAndPgPaymentIdIsNullOrderByDepositedAtAsc(
            VirtualAccountPaymentStatus status
    );

    // 입금과 만료가 같은 계좌 행을 잠가 상태 변경을 순서대로 처리한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DummyVirtualAccount> findTop100ByPgPaymentIdIsNullAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            VirtualAccountPaymentStatus status,
            LocalDateTime expiresAt
    );
}
