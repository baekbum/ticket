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

    // 기존 은행 입금 자료 중 통보 의도가 없는 행만 은행 책임으로 Outbox에 편입한다.
    @Query(value = """
            select a.pg_payment_id from dummy_virtual_accounts a
             where a.pg_payment_id is not null and a.deposited_at is not null
               and not exists (select 1 from bank_transfer_outbox e
                                where e.pg_payment_id = a.pg_payment_id and e.flow = 'BANK_DEPOSIT')
             order by a.deposited_at, a.pg_payment_id
            """, nativeQuery = true)
    List<String> findUnnotifiedDepositIds(org.springframework.data.domain.Pageable page);

    // 신규 금융 입금은 PG 연결 계좌만 잠근다. 기존 paymentNo 전용 경로와 섞지 않는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DummyVirtualAccount a where a.accountNumber = :number and a.pgPaymentId is not null")
    Optional<DummyVirtualAccount> findLinkedByAccountNumberForUpdate(@Param("number") String number);

    // 취소 예매 응답을 정리할 때도 같은 은행 계좌 잠금을 사용한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DummyVirtualAccount a where a.pgPaymentId = :id")
    Optional<DummyVirtualAccount> findLinkedByPgPaymentIdForUpdate(@Param("id") String id);

    // 신규 은행 만료는 계좌 ID만 읽고 건별 트랜잭션에서 입금과 같은 계좌 행을 잠근다.
    @Query("""
            select a.pgPaymentId from DummyVirtualAccount a
            where a.pgPaymentId is not null and a.pgPaymentId > :afterId
              and a.status = :status
              and a.expiresAt < :now
            order by a.pgPaymentId
            """)
    List<String> findLinkedExpirationCandidates(
            @Param("now") LocalDateTime now,
            @Param("afterId") String afterId,
            @Param("status") VirtualAccountPaymentStatus status,
            org.springframework.data.domain.Pageable page
    );

    boolean existsByAccountNumber(String accountNumber);
}
